#!/usr/bin/env bash
# Rolls the ECS service onto IMAGE if the app stack is running; otherwise skips.
# Fails the build if the deployment is rolled back by the circuit breaker.
set -euo pipefail

IMAGE="$1"
CLUSTER="$ECS_CLUSTER"
SERVICE="$ECS_SERVICE"

status=$(aws ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" \
  --query 'services[0].status' --output text 2>/dev/null || echo "NONE")

if [ "$status" != "ACTIVE" ]; then
  echo "App stack is not running (service status: $status)."
  echo "Image $IMAGE is published; the next 'terraform apply' of infra/app will deploy it."
  exit 0
fi

current_td=$(aws ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" \
  --query 'services[0].taskDefinition' --output text)

# Copy the running task definition, swapping only the image.
new_def=$(aws ecs describe-task-definition --task-definition "$current_td" \
  | jq --arg img "$IMAGE" '.taskDefinition
      | {family, executionRoleArn, networkMode, containerDefinitions,
         requiresCompatibilities, cpu, memory, runtimePlatform}
      | .containerDefinitions[0].image = $img')

new_td=$(aws ecs register-task-definition --cli-input-json "$new_def" \
  --query 'taskDefinition.taskDefinitionArn' --output text)
echo "Registered $new_td"

# Start the rollout and remember which deployment is ours.
deployment_id=$(aws ecs update-service --cluster "$CLUSTER" --service "$SERVICE" \
  --task-definition "$new_td" \
  --query 'service.deployments[?status==`PRIMARY`].id | [0]' --output text)
echo "Deployment $deployment_id started for $new_td; waiting for ECS to report the outcome..."

# ECS's rolloutState is authoritative: COMPLETED = healthy and steady,
# FAILED = circuit breaker rolled it back. If our deployment disappears
# from the list, it was replaced by a rollback, which is also a failure.
deadline=$((SECONDS + 1200))
while [ "$SECONDS" -lt "$deadline" ]; do
  state=$(aws ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" \
    --query "services[0].deployments[?id=='${deployment_id}'].rolloutState | [0]" --output text)
  case "$state" in
    COMPLETED)
      echo "Deployed $IMAGE ($new_td)"
      exit 0
      ;;
    FAILED|None)
      echo "Deployment $deployment_id FAILED or was rolled back (rolloutState=$state)" >&2
      exit 1
      ;;
    *)
      echo "rolloutState=$state; checking again in 15s"
      sleep 15
      ;;
  esac
done

echo "Timed out after 20 minutes waiting for deployment $deployment_id" >&2
exit 1