locals {
  db_secret_arn = aws_db_instance.this.master_user_secret[0].secret_arn
}

resource "aws_ecs_cluster" "this" {
  name = var.project

  setting {
    name  = "containerInsights"
    value = "disabled"
  }
}

resource "aws_ecs_task_definition" "app" {
  family                   = var.project
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.task_execution.arn
  # No task role: the app itself never calls AWS APIs, so it gets no AWS permissions.

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([
    {
      name      = "app"
      image     = "${local.ecr_repository_url}:${var.image_tag}"
      essential = true

      portMappings = [{ containerPort = 8080, protocol = "tcp" }]

      environment = [
        {
          name  = "DB_URL"
          value = "jdbc:postgresql://${aws_db_instance.this.address}:5432/${aws_db_instance.this.db_name}?sslmode=require"
        }
      ]

      # Injected by the ECS agent at start; never stored in the task definition.
      secrets = [
        { name = "DB_USERNAME", valueFrom = "${local.db_secret_arn}:username::" },
        { name = "DB_PASSWORD", valueFrom = "${local.db_secret_arn}:password::" }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.app.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "app"
        }
      }

      stopTimeout = 30
    }
  ])
}

resource "aws_ecs_service" "app" {
  name            = var.project
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  # Spring Boot on 0.25 vCPU needs time to start before health checks count.
  health_check_grace_period_seconds = 180

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  # A deployment that never becomes healthy is rolled back automatically.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = local.public_subnet_ids
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "app"
    container_port   = 8080
  }

  wait_for_steady_state = true

  depends_on = [aws_lb_listener.http, aws_iam_role_policy.read_db_secret]
}