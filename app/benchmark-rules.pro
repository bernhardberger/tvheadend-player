# AndroidJUnitRunner can reference these shared classes from the optimized target.
# Keep only the tracing bridge, not the whole androidx namespace. ProfileServer
# inherits this file from profile; ordinary release builds do not use it.
-keep class androidx.tracing.Trace { *; }
-keep class androidx.tracing.TraceApi18Impl { *; }
-keep class androidx.tracing.TraceApi29Impl { *; }
