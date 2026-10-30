data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, 2)
}

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 6.6"

  name = var.project
  cidr = var.vpc_cidr
  azs  = local.azs

  # Public: ALB and Fargate tasks (tasks get a public IP for outbound access).
  public_subnets = ["10.0.0.0/24", "10.0.1.0/24"]

  # Private: RDS only, with no route to the internet.
  private_subnets = ["10.0.10.0/24", "10.0.11.0/24"]

  # Deliberately no NAT gateway: it's the most expensive part of a small VPC.
  enable_nat_gateway = false
  enable_vpn_gateway = false

  enable_dns_hostnames = true
  enable_dns_support   = true

  public_subnet_tags  = { Tier = "public" }
  private_subnet_tags = { Tier = "private" }
}