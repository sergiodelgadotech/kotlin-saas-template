// Web static module
// Manages the landing page and documentation site

tasks.register<Exec>("buildCss") {
    description = "Compile Tailwind CSS"
    commandLine("npx", "tailwindcss",
        "-i", "assets/css/input.css",
        "-o", "assets/css/output.css",
        "--minify"
    )
    onlyIf { file("assets/css/input.css").exists() }
}

tasks.register<Exec>("deploy") {
    description = "Deploy static site to Cloudflare Pages"
    dependsOn("buildCss")
    commandLine("npx", "wrangler", "pages", "deploy", ".",
        "--project-name=kotlin-saas-web"
    )
}

tasks.register("build") {
    dependsOn("buildCss")
}
