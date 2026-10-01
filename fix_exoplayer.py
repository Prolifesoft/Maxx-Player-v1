import sys
import re

content = open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt").read()

if "DefaultHttpDataSource" not in content:
    content = content.replace("import androidx.media3.exoplayer.DefaultRenderersFactory", "import androidx.media3.exoplayer.DefaultRenderersFactory\nimport androidx.media3.datasource.DefaultHttpDataSource\nimport androidx.media3.exoplayer.source.DefaultMediaSourceFactory\nimport androidx.media3.datasource.DefaultDataSource")
    
    builder_pattern = r"ExoPlayer\.Builder\(context, renderersFactory\)"
    builder_replace = """val dataSourceFactory = DefaultDataSource.Factory(
            context,
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)"""
            
    content = re.sub(builder_pattern, builder_replace, content)
    
    open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w").write(content)
    print("ExoPlayer redirects fixed")
else:
    print("Already fixed")
