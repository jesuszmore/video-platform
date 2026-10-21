resource "aws_sqs_queue" "transcode_dlq" {
  name                      = "${var.prefix}-transcode-dlq"
  kms_master_key_id         = aws_kms_key.main.id
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "transcode" {
  name                       = "${var.prefix}-transcode"
  visibility_timeout_seconds = 3600
  message_retention_seconds  = 86400
  kms_master_key_id          = aws_kms_key.main.id

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.transcode_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue_redrive_allow_policy" "transcode_dlq" {
  queue_url = aws_sqs_queue.transcode_dlq.id
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.transcode.arn]
  })
}
