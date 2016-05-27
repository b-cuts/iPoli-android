package io.ipoli.android.quest.viewmodels;

import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.text.TextUtils;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.ocpsoft.prettytime.shade.net.fortuna.ical4j.model.Date;
import org.ocpsoft.prettytime.shade.net.fortuna.ical4j.model.DateTime;
import org.ocpsoft.prettytime.shade.net.fortuna.ical4j.model.Recur;
import org.ocpsoft.prettytime.shade.net.fortuna.ical4j.model.parameter.Value;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import io.ipoli.android.app.utils.DateUtils;
import io.ipoli.android.app.utils.Time;
import io.ipoli.android.quest.QuestContext;
import io.ipoli.android.quest.data.RepeatingQuest;
import io.ipoli.android.quest.data.Recurrence;
import io.ipoli.android.quest.ui.formatters.DurationFormatter;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 4/9/16.
 */
public class RepeatingQuestViewModel {

    private final RepeatingQuest repeatingQuest;
    private final int completedCount;
    private final Recur recur;
    private final java.util.Date nextDate;
    private final int timesPerDay;

    public RepeatingQuestViewModel(RepeatingQuest repeatingQuest, int completedCount, Recur recur, java.util.Date nextDate) {
        this.repeatingQuest = repeatingQuest;
        this.completedCount = completedCount;
        this.recur = recur;
        this.nextDate = nextDate;
        int timesPerDay = 1;
        try {
            Recurrence recurrence = repeatingQuest.getRecurrence();
            if (!TextUtils.isEmpty(recurrence.getDailyRrule())) {
                timesPerDay = new Recur(repeatingQuest.getRecurrence().getDailyRrule()).getCount();
            }
        } catch (ParseException e) {
            timesPerDay = 1;
        } finally {
            this.timesPerDay = timesPerDay;
        }

    }

    public String getName() {
        return repeatingQuest.getName();
    }

    @ColorRes
    public int getContextColor() {
        return getQuestContext().resLightColor;
    }

    @DrawableRes
    public int getContextImage() {
        return getQuestContext().whiteImage;
    }

    public int getCompletedDailyCount() {
        return (int) Math.floor((double) completedCount / (double) timesPerDay);
    }

    public int getRemainingDailyCount() {
        return (int) Math.ceil((double) (getTotalCount() - completedCount) / (double) timesPerDay);
    }

    private QuestContext getQuestContext() {
        return RepeatingQuest.getContext(repeatingQuest);
    }

    public String getNextText() {
        String nextText = "";
        if (recur == null || nextDate == null) {
            nextText += "Unscheduled";
        } else {
            if (DateUtils.isTodayUTC(nextDate)) {
                nextText = "Today";
            } else if (DateUtils.isTomorrowUTC(nextDate)) {
                nextText = "Tomorrow";
            } else {
                nextText = new SimpleDateFormat("dd MMM", Locale.getDefault()).format(nextDate);
            }
        }

        nextText += " ";

        int duration = repeatingQuest.getDuration();
        Time startTime = RepeatingQuest.getStartTime(repeatingQuest);
        if (duration > 0 && startTime != null) {
            Time endTime = Time.plusMinutes(startTime, duration);
            nextText += startTime + " - " + endTime;
        } else if (duration > 0) {
            nextText += "for " + DurationFormatter.formatReadable(duration);
        } else if (startTime != null) {
            nextText += startTime;
        }
        return "Next: " + nextText;
    }

    public int getTotalCount() {
        if (recur == null) {
            return -1;
        }
        if (recur.getFrequency().equals(Recur.MONTHLY)) {
            return 1;
        }

        java.util.Date from = LocalDate.now().dayOfWeek().withMinimumValue().toDateTimeAtStartOfDay(DateTimeZone.UTC).toDate();
        java.util.Date to = LocalDate.now().dayOfWeek().withMaximumValue().toDateTimeAtStartOfDay(DateTimeZone.UTC).toDate();

        return recur.getDates(new DateTime(repeatingQuest.getRecurrence().getDtstart()), new Date(from), new Date(to), Value.DATE_TIME).size() * timesPerDay;
    }

    public String getRepeatText() {
        if (recur == null) {
            return "";
        }

        int remainingCount = getRemainingDailyCount();

        if (remainingCount <= 0) {
            return "Done";
        }

        if (recur.getFrequency().equals(Recur.MONTHLY)) {
            return remainingCount + " more this month";
        }

        return remainingCount + " more this week";

    }

    public RepeatingQuest getRepeatingQuest() {
        return repeatingQuest;
    }
}