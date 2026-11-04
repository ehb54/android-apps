// Framework for desktop apps (desktop/<app>/), the counterpart of core/ui:
// theme, build info, Android-style resources (R, stringResource, painterResource),
// back handling, file dialogs and app folders.
plugins {
    alias(libs.plugins.ehb54.desktop.library)
}

androidResources {
    // core/ui's strings and icons, so both frameworks show the same.
    resDirs.from(rootDir.resolve("core/ui/src/main/res"))
}
