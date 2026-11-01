# Read the core layer's outputs (VPC, subnets, ECR) from its state file.
data "terraform_remote_state" "core" {
  backend = "s3"

  config = {
    bucket = var.state_bucket
    key    = "ledger-api/terraform.tfstate"
    region = var.region
  }
}

locals {
  vpc_id             = data.terraform_remote_state.core.outputs.vpc_id
  public_subnet_ids  = data.terraform_remote_state.core.outputs.public_subnet_ids
  private_subnet_ids = data.terraform_remote_state.core.outputs.private_subnet_ids
}