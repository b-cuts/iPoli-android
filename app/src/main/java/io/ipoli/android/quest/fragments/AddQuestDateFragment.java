package io.ipoli.android.quest.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.joda.time.LocalDate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.ipoli.android.R;
import io.ipoli.android.app.App;
import io.ipoli.android.app.BaseFragment;
import io.ipoli.android.app.ui.dialogs.DatePickerFragment;
import io.ipoli.android.quest.adapters.QuestOptionsAdapter;
import io.ipoli.android.quest.data.Category;
import io.ipoli.android.quest.events.NewQuestDatePickedEvent;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 1/7/17.
 */

public class AddQuestDateFragment extends BaseFragment {

    @BindView(R.id.new_quest_date_options)
    RecyclerView dateOptions;

    @BindView(R.id.new_quest_date_image)
    ImageView image;

    private Unbinder unbinder;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        App.getAppComponent(getContext()).inject(this);
        View view = inflater.inflate(R.layout.fragment_wizard_quest_date, container, false);
        unbinder = ButterKnife.bind(this, view);

        dateOptions.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        dateOptions.setHasFixedSize(true);

        List<Pair<String, View.OnClickListener>> options = new ArrayList<>();

        options.add(new Pair<>(getString(R.string.by_the_end_of_week), v ->
                postEvent(new NewQuestDatePickedEvent(LocalDate.now(), LocalDate.now().dayOfWeek().withMaximumValue()))));

        options.add(new Pair<>(getString(R.string.by_the_end_of_month), v ->
                postEvent(new NewQuestDatePickedEvent(LocalDate.now(), LocalDate.now().dayOfMonth().withMaximumValue()))));

        options.add(new Pair<>(getString(R.string.someday_by), v -> {
            DatePickerFragment fragment = DatePickerFragment.newInstance(new Date(), true, false,
                    date -> postEvent(new NewQuestDatePickedEvent(LocalDate.now(), new LocalDate(date.getTime()))));
            fragment.show(getFragmentManager());
        }));

        options.add(new Pair<>(getString(R.string.today), v ->
                postEvent(new NewQuestDatePickedEvent(LocalDate.now(), LocalDate.now()))));

        options.add(new Pair<>(getString(R.string.tomorrow), v ->
                postEvent(new NewQuestDatePickedEvent(LocalDate.now().plusDays(1), LocalDate.now().plusDays(1)))));

        options.add(new Pair<>(getString(R.string.on), v -> {
            DatePickerFragment fragment = DatePickerFragment.newInstance(new Date(), true, false,
                    date -> {
                        LocalDate onDate = new LocalDate(date.getTime());
                        postEvent(new NewQuestDatePickedEvent(onDate, onDate));
                    });
            fragment.show(getFragmentManager());
        }));

        options.add(new Pair<>(getString(R.string.do_not_know), v -> {
            postEvent(new NewQuestDatePickedEvent(null, null));
        }));

        dateOptions.setAdapter(new QuestOptionsAdapter(options));


        return view;
    }

    public void setCategory(Category category) {
        switch (category) {
            case LEARNING:
                image.setImageResource(R.drawable.new_learning_quest);
                break;
            case WELLNESS:
                image.setImageResource(R.drawable.new_wellness_quest);
                break;
            case WORK:
                image.setImageResource(R.drawable.new_work_quest);
                break;
            case PERSONAL:
                image.setImageResource(R.drawable.new_personal_quest);
                break;
            case FUN:
                image.setImageResource(R.drawable.new_fun_quest);
                break;
            case CHORES:
                image.setImageResource(R.drawable.new_chores_quest);
                break;
        }
    }

    @Override
    public void onDestroyView() {
        unbinder.unbind();
        super.onDestroyView();
    }

    @Override
    protected boolean useOptionsMenu() {
        return false;
    }
}
