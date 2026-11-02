variable "region" {
  description = "AWS region of the project (fixed by the AWS project settings)"
  type        = string
  default     = "us-east-2"
}

variable "project" {
  description = "Name prefix for all resources"
  type        = string
  default     = "ledger-api"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "alert_email" {
  description = "Email address that receives CloudWatch alarm notifications"
  type        = string
}

variable "github_repository" {
  description = "GitHub repository (owner/name) the pipeline builds; case-sensitive"
  type        = string
  default     = "NickLesho/ledger-api"
}