name := "embedded-spray-build"

// In your editor, add the root folder manually as a source
// It should only include *.scala (nothing from sub directories)
// Settings below prevent other directories from being created

unmanagedSourceDirectories in Compile := Seq()

unmanagedSourceDirectories in Test := Seq()
