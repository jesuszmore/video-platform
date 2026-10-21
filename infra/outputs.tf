output "alb_dns_name" {
  description = "Endpoint publico de la API"
  value       = "http://${aws_lb.main.dns_name}"
}

output "cloudfront_domain" {
  description = "Dominio de entrega de HLS y miniaturas"
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "raw_bucket" {
  value = aws_s3_bucket.raw.bucket
}

output "processed_bucket" {
  value = aws_s3_bucket.processed.bucket
}

output "transcode_queue_url" {
  value = aws_sqs_queue.transcode.url
}

output "transcode_dlq_url" {
  value = aws_sqs_queue.transcode_dlq.url
}

output "backend_repository_url" {
  value = aws_ecr_repository.backend.repository_url
}

output "worker_repository_url" {
  value = aws_ecr_repository.worker.repository_url
}

output "aurora_endpoint" {
  value = aws_rds_cluster.main.endpoint
}

output "kms_key_alias" {
  value = aws_kms_alias.main.name
}
