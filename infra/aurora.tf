resource "aws_db_subnet_group" "main" {
  name       = "${var.prefix}-db"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_security_group" "db" {
  name        = "${var.prefix}-db"
  description = "Acceso a Aurora desde backend y worker"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.backend.id, aws_security_group.worker.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_rds_cluster" "main" {
  cluster_identifier          = "${var.prefix}-aurora"
  engine                      = "aurora-postgresql"
  engine_mode                 = "provisioned"
  database_name               = "videos"
  master_username             = "app"
  manage_master_user_password = true
  db_subnet_group_name        = aws_db_subnet_group.main.name
  vpc_security_group_ids      = [aws_security_group.db.id]

  serverlessv2_scaling_configuration {
    min_capacity = var.db_min_acu
    max_capacity = var.db_max_acu
  }

  storage_encrypted   = true
  kms_key_id          = aws_kms_key.main.arn
  skip_final_snapshot = true
}

resource "aws_rds_cluster_instance" "main" {
  identifier         = "${var.prefix}-aurora-1"
  cluster_identifier = aws_rds_cluster.main.id
  instance_class     = "db.serverless"
  engine             = aws_rds_cluster.main.engine
}
