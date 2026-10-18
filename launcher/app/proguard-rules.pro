# The launcher's keep rules for R8 (release builds). Activities and the views named in layouts are kept by the
# rules aapt writes; these cover what is reached by name rather than from code.

# Custom views in layouts keep their XML constructors (aapt keeps the classes; this keeps the constructors whole).
-keepclassmembers class com.gathin.tvhome.** extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Fonts and resources read by id from both modules stay (shrinkResources sees direct R references).
-keep class com.gathin.tvshared.R$* { *; }

# Stack traces stay readable in logcat.
-keepattributes SourceFile,LineNumberTable
