package io.ipoli.android.quest.parsers;

import org.ocpsoft.prettytime.nlp.PrettyTimeParser;

import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.ipoli.android.app.utils.Time;
import io.ipoli.android.quest.suggestions.MatcherType;
import io.ipoli.android.quest.suggestions.TextEntityType;
import io.ipoli.android.quest.suggestions.providers.StartTimeSuggestionsProvider;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 2/19/16.
 */
public class StartTimeMatcher extends BaseMatcher<Integer> {

    private static final String PATTERN = "(?:^|\\s)at (\\d{1,2}([:|\\.]\\d{2})?(\\s?(am|pm))?)(?:$|\\s)";
    private final PrettyTimeParser parser;
    private Pattern pattern = Pattern.compile(PATTERN, Pattern.CASE_INSENSITIVE);

    public StartTimeMatcher(PrettyTimeParser parser) {
        super(new StartTimeSuggestionsProvider());
        this.parser = parser;
    }

    @Override
    public Match match(String text) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return new Match(m.group(), m.start(), m.end() - 1);
        }
        return null;
    }

    @Override
    public Integer parse(String text) {
        Matcher stm = pattern.matcher(text);
        if (stm.find()) {
            List<Date> dates = parser.parse(stm.group());
            if (!dates.isEmpty()) {
                return Time.of(dates.get(0)).toMinutesAfterMidnight();
            }
        }
        return -1;
    }

    @Override
    public MatcherType getMatcherType() {
        return MatcherType.TIME;
    }

    @Override
    public TextEntityType getTextEntityType() {
        return TextEntityType.START_TIME;
    }

    @Override
    public boolean partiallyMatches(String text) {
        Matcher m = pattern.matcher(text);
        m.matches();
        return m.hitEnd();
    }
}
