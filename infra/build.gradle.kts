tasks.register<Exec>("init") {
    description = "Initialize Terraform"
    commandLine("terraform", "init")
    workingDir = projectDir
}

tasks.register<Exec>("plan") {
    description = "Terraform plan"
    commandLine("terraform", "plan")
    workingDir = projectDir
}

tasks.register<Exec>("apply") {
    description = "Terraform apply"
    commandLine("terraform", "apply", "-auto-approve")
    workingDir = projectDir
}
