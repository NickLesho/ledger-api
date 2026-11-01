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