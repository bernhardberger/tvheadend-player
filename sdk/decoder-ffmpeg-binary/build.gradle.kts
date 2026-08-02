val defaultConfiguration = configurations.maybeCreate("default")

artifacts.add(
    defaultConfiguration.name,
    rootProject.layout.projectDirectory.file(
        "app/libs/lib-decoder-ffmpeg-release.aar",
    ).asFile,
) {
    type = "aar"
    extension = "aar"
}
