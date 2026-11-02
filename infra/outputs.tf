output "vpc_id" {
  value = module.vpc.vpc_id
}

output "public_subnet_ids" {
  value = module.vpc.public_subnets
}

output "private_subnet_ids" {
  value = module.vpc.private_subnets
}

output "azs" {
  value = local.azs
}

output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "image_tag_parameter" {
  value = aws_ssm_parameter.image_tag.name
}

output "github_connection_arn" {
  value = aws_codestarconnections_connection.github.arn
}

output "pipeline_name" {
  value = aws_codepipeline.app.name
}