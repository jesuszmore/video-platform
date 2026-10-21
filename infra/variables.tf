variable "region" {
  type        = string
  description = "Region de AWS"
  default     = "eu-west-1"
}

variable "prefix" {
  type        = string
  description = "Prefijo de nombres de recursos"
  default     = "video-platform"
}

variable "app_origin" {
  type        = string
  description = "Origen permitido en CORS (URL del frontend)"
  default     = "http://localhost:4200"
}

variable "jwt_issuer" {
  type        = string
  description = "Issuer OIDC del proveedor de identidad (Cognito)"
}

variable "worker_image" {
  type        = string
  description = "Imagen del worker en ECR (url:tag)"
  default     = ""
}

variable "backend_image" {
  type        = string
  description = "Imagen del backend en ECR (url:tag)"
  default     = ""
}

variable "backend_cpu" {
  type    = number
  default = 256
}

variable "backend_memory" {
  type    = number
  default = 512
}

variable "worker_cpu" {
  type    = number
  default = 2048
}

variable "worker_memory" {
  type    = number
  default = 4096
}

variable "worker_max_tasks" {
  type    = number
  default = 10
}

variable "db_min_acu" {
  type        = number
  description = "ACU minimo de Aurora Serverless v2 (0 = escala a cero)"
  default     = 0
}

variable "db_max_acu" {
  type    = number
  default = 4
}
