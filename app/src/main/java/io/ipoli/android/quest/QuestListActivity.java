package io.ipoli.android.quest;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.util.List;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import io.ipoli.android.Constants;
import io.ipoli.android.R;
import io.ipoli.android.app.BaseActivity;
import io.ipoli.android.app.ui.ItemTouchCallback;
import io.ipoli.android.quest.events.CompleteQuestEvent;
import io.ipoli.android.quest.events.QuestCompleteRequestEvent;
import io.ipoli.android.quest.events.QuestUpdatedEvent;
import io.ipoli.android.quest.events.UndoCompleteQuestEvent;
import io.ipoli.android.quest.persistence.QuestPersistenceService;

public class QuestListActivity extends BaseActivity {

    @Bind(R.id.quest_list_container)
    LinearLayout rootContainer;

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Inject
    Bus eventBus;

    @Bind(R.id.quest_list)
    RecyclerView questList;

    @Inject
    QuestPersistenceService questPersistenceService;

    private QuestAdapter questAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quest_list);
        ButterKnife.bind(this);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        appComponent().inject(this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        questList.setLayoutManager(layoutManager);

        List<Quest> quests = questPersistenceService.findAllPlannedForToday();
        questAdapter = new QuestAdapter(quests, eventBus);
        questList.setAdapter(questAdapter);

        int swipeFlags = ItemTouchHelper.END;
        ItemTouchCallback touchCallback = new ItemTouchCallback(questAdapter, swipeFlags);
        ItemTouchHelper helper = new ItemTouchHelper(touchCallback);
        helper.attachToRecyclerView(questList);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        eventBus.register(this);
    }

    @Override
    public void onPause() {
        eventBus.unregister(this);
        saveQuestsOrder();
        super.onPause();
    }

    private void saveQuestsOrder() {
        List<Quest> quests = questAdapter.getQuests();
        int order = 0;
        for (Quest q : quests) {
            q.setOrder(order);
            order++;
        }
        questPersistenceService.saveAll(quests);
    }

    @Subscribe
    public void onQuestCompleteRequest(final QuestCompleteRequestEvent e) {
        final Snackbar snackbar = Snackbar
                .make(rootContainer,
                        String.format(getString(R.string.increase_experience), Constants.COMPLETE_QUEST_DEFAULT_EXPERIENCE),
                        Snackbar.LENGTH_LONG);

        snackbar.setCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                super.onDismissed(snackbar, event);
                Quest q = e.quest;
                q.setStatus(Quest.Status.COMPLETED.name());
                questPersistenceService.save(q);
                eventBus.post(new CompleteQuestEvent(q));
            }

        });

        snackbar.setAction(R.string.undo, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                questAdapter.addQuest(e.position, e.quest);
                snackbar.setCallback(null);
                eventBus.post(new UndoCompleteQuestEvent(e.quest));
            }
        });

        snackbar.show();
    }

    @Subscribe
    public void onQuestStatusChanged(QuestUpdatedEvent e) {
        questPersistenceService.save(e.quest);
        Quest.Status status = Quest.Status.valueOf(e.quest.getStatus());
        String m = "";
        if (status == Quest.Status.STARTED) {
            m = getString(R.string.quest_started);
        } else if (status == Quest.Status.PLANNED) {
            m = getString(R.string.quest_stopped);
        }

        if (!TextUtils.isEmpty(m)) {
            Snackbar.make(rootContainer, m, Snackbar.LENGTH_SHORT).show();
        }

    }
}