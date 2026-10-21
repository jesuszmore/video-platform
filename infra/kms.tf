resource "aws_kms_key" "main" {
  description             = "${var.prefix} cifrado de S3/SQS"
  deletion_window_in_days = 7
  enable_key_rotation     = true
}

resource "aws_kms_alias" "main" {
  name          = "alias/${var.prefix}"
  target_key_id = aws_kms_key.main.key_id
}
