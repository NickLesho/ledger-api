variable "region" {
  description = "AWS region of the project"
  type        = string
  default     = "us-east-2"
}

variable "project" {
  description = "Name prefix for all resources"
  type        = string
  default     = "ledger-api"
}

variable "state_bucket" {
  description = "S3 bucket holding Terraform state for both layers"
  type        = string
  default     = "ledger-api-tfstate-754947719417"
}

variable "db_instance_class" {
  description = "RDS instance size"
  type        = string
  default     = "db.t4g.micro"
}

variable "image_tag" {
  description = "ECR image tag (git short SHA) to deploy"
  type        = string
}

variable "allowed_cidrs" {
  description = "Client CIDR blocks allowed to reach the ALB, e.g. your IP as x.x.x.x/32"
  type        = list(string)

  validation {
    condition     = length(var.allowed_cidrs) > 0 && alltrue([for c in var.allowed_cidrs : can(cidrhost(c, 0))])
    error_message = "allowed_cidrs must be a non-empty list of valid CIDR blocks."
  }
}