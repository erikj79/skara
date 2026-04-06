package org.openjdk.skara.bots.pr;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openjdk.skara.host.HostUser;
import org.openjdk.skara.issuetracker.Comment;
import org.openjdk.skara.vcs.openjdk.CommitMessage;

class Trailers {
    private static final String SET_MARKER = "<!-- set trailer: '%s' '%s' -->";
    private static final String REMOVE_MARKER = "<!-- remove trailer: '%s' -->";

    private static final Pattern MARKER_PATTERN = Pattern.compile(
            "<!-- (?:(set) trailer: '([\\p{Alnum}-]+?)' '(.*?)'|(remove) trailer: '([\\p{Alnum}-]+?)') -->");

    static String setTrailerMarker(String key, String value) {
        return String.format(SET_MARKER, key, value);
    }

    static String removeTrailerMarker(String key) {
        return String.format(REMOVE_MARKER, key);
    }

    static List<CommitMessage.CustomTrailer> trailers(HostUser botUser, List<Comment> comments) {
        var trailerActions = comments.stream()
                .filter(comment -> comment.author().equals(botUser))
                .map(comment -> MARKER_PATTERN.matcher(comment.body()))
                .filter(Matcher::find)
                .toList();
        var trailers = new LinkedHashMap<String, CommitMessage.CustomTrailer>();
        for (Matcher action : trailerActions) {
            if ("set".equals(action.group(1))) {
                trailers.put(action.group(2), new CommitMessage.CustomTrailer(action.group(2), action.group(3)));
            } else if ("remove".equals(action.group(4))) {
                trailers.remove(action.group(5));
            }
        }
        return List.copyOf(trailers.sequencedValues());
    }
}
