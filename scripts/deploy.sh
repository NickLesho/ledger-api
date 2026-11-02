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

aws ecs update-service --cluster "$CLUSTER" --service "$SERVICE" --task-definition "$new_td" > /dev/null
echo "Rolling deployment started; waiting for the service to stabilise..."
aws ecs wait services-stable --cluster "$CLUSTER" --services "$SERVICE"

# "Stable" is also true after an automatic rollback, so check what actually won.
primary_td=$(aws ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" \
  --query 'services[0].taskDefinition' --output text)
rollout=$(aws ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" \
  --query 'services[0].deployments[?status==`PRIMARY`].rolloutState | [0]' --output text)

if [ "$primary_td" != "$new_td" ] || [ "$rollout" != "COMPLETED" ]; then
  echo "Deployment FAILED or was rolled back (rolloutState=$rollout, running=$primary_td)" >&2
  exit 1
fi

echo "Deployed $IMAGE"