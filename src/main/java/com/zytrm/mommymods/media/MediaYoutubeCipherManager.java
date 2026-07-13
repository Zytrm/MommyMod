package com.zytrm.mommymods.media;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.zytrm.mommymods.MommyMods;
import dev.lavalink.youtube.cipher.LocalSignatureCipherManager;
import dev.lavalink.youtube.track.format.StreamFormat;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MediaYoutubeCipherManager extends LocalSignatureCipherManager {
    private static final Pattern REGULAR_SCRIPT =
        Pattern.compile("/s/player/[a-f0-9]+/player(?!_embed)[^\"/]*\\.js");

    private volatile String regularScriptUrl;

    @Override
    public URI resolveFormatUrl(HttpInterface http, String playerScriptUrl, StreamFormat format) throws IOException {
        try {
            return super.resolveFormatUrl(http, playerScriptUrl, format);
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            if (message != null && message.toLowerCase().contains("must find")) {
                String fallback = findRegularScript(http);
                if (fallback != null && !fallback.equals(playerScriptUrl)) {
                    return super.resolveFormatUrl(http, fallback, format);
                }
            }
            throw exception;
        }
    }

    private String findRegularScript(HttpInterface http) {
        if (regularScriptUrl != null) return regularScriptUrl;
        try (CloseableHttpResponse response = http.execute(new HttpGet("https://www.youtube.com/"))) {
            String html = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            Matcher matcher = REGULAR_SCRIPT.matcher(html);
            if (matcher.find()) {
                regularScriptUrl = matcher.group();
                return regularScriptUrl;
            }
        } catch (Exception exception) {
            MommyMods.INSTANCE.getLogger().debug("Could not resolve a fallback YouTube player script", exception);
        }
        return null;
    }
}
