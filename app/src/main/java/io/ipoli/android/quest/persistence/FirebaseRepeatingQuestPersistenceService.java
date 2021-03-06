package io.ipoli.android.quest.persistence;

import android.support.annotation.NonNull;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.Query;
import com.squareup.otto.Bus;

import org.joda.time.LocalDate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.ipoli.android.app.persistence.BaseFirebasePersistenceService;
import io.ipoli.android.app.utils.StringUtils;
import io.ipoli.android.challenge.data.Challenge;
import io.ipoli.android.quest.data.Quest;
import io.ipoli.android.quest.data.QuestData;
import io.ipoli.android.quest.data.RepeatingQuest;

import static io.ipoli.android.app.utils.DateUtils.toStartOfDayUTC;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 7/27/16.
 */
public class FirebaseRepeatingQuestPersistenceService extends BaseFirebasePersistenceService<RepeatingQuest> implements RepeatingQuestPersistenceService {

    private final QuestPersistenceService questPersistenceService;

    public FirebaseRepeatingQuestPersistenceService(Bus eventBus, QuestPersistenceService questPersistenceService) {
        super(eventBus);
        this.questPersistenceService = questPersistenceService;
    }

    @Override
    protected DatabaseReference getCollectionReference() {
        return getPlayerReference().child(getCollectionName());
    }

    @Override
    protected GenericTypeIndicator<Map<String, RepeatingQuest>> getGenericMapIndicator() {
        return new GenericTypeIndicator<Map<String, RepeatingQuest>>() {
        };
    }

    @Override
    protected Class<RepeatingQuest> getModelClass() {
        return RepeatingQuest.class;
    }

    @Override
    protected String getCollectionName() {
        return "repeatingQuests";
    }

    @Override
    public void findAllNonAllDayActiveRepeatingQuests(OnDataChangedListener<List<RepeatingQuest>> listener) {
        Query query = getCollectionReference().orderByChild("allDay").equalTo(false);
        listenForListChange(query, listener, createActiveRepeatingQuestPredicate());
    }

    @Override
    public void listenForAllNonAllDayActiveRepeatingQuests(OnDataChangedListener<List<RepeatingQuest>> listener) {
        Query query = getCollectionReference().orderByChild("allDay").equalTo(false);
        listenForListChange(query, listener, createActiveRepeatingQuestPredicate());
    }

    @Override
    public void listenForNonFlexibleNonAllDayActiveRepeatingQuests(OnDataChangedListener<List<RepeatingQuest>> listener) {
        Query query = getCollectionReference().orderByChild("recurrence/flexibleCount").equalTo(0);
        listenForListChange(query, listener, createActiveRepeatingQuestPredicate());
    }

    @Override
    public void findActiveNotForChallenge(String searchText, Challenge challenge, OnDataChangedListener<List<RepeatingQuest>> listener) {
        listenForSingleListChange(getCollectionReference(), listener, rq ->
                !challenge.getId().equals(rq.getChallengeId()) &&
                        rq.getName().toLowerCase().contains(searchText.toLowerCase()) &&
                        (rq.getRecurrence().getDtendDate() == null ||
                                rq.getRecurrence().getDtendDate().getTime() >= toStartOfDayUTC(LocalDate.now()).getTime())
        );
    }

    @Override
    public void save(RepeatingQuest repeatingQuest, List<Quest> quests) {
        Map<String, Object> data = new HashMap<>();
        populateNewRepeatingQuest(data, repeatingQuest, quests);
        getPlayerReference().updateChildren(data);
    }

    @Override
    public void delete(RepeatingQuest repeatingQuest, List<Quest> quests) {
        Map<String, Object> data = new HashMap<>();
        if (!StringUtils.isEmpty(repeatingQuest.getChallengeId())) {
            data.put("/challenges/" + repeatingQuest.getChallengeId() + "/repeatingQuestIds/" + repeatingQuest.getId(), null);
            data.put("/challenges/" + repeatingQuest.getChallengeId() + "/challengeRepeatingQuests/" + repeatingQuest.getId(), null);
        }
        data.put("/repeatingQuests/" + repeatingQuest.getId(), null);

        Set<String> orphanQuestIds = repeatingQuest.getQuestsData().keySet();
        for (Quest quest : quests) {
            orphanQuestIds.remove(quest.getId());
            questPersistenceService.populateDeleteQuestDataFromRepeatingQuest(quest, data);
        }

        for (String questId : orphanQuestIds) {
            data.put("/quests/" + questId + "/repeatingQuestId", null);
        }
        getPlayerReference().updateChildren(data);
    }

    @Override
    public void update(RepeatingQuest repeatingQuest, List<Quest> questsToRemove, List<Quest> questsToCreate) {
        Map<String, Object> data = new HashMap<>();

        populateRepeatingQuestChallenge(repeatingQuest, data);

        for (Quest quest : questsToRemove) {
            questPersistenceService.populateDeleteQuestDataFromRepeatingQuest(quest, data);
            repeatingQuest.getQuestsData().remove(quest.getId());
        }

        for (Quest quest : questsToCreate) {
            questPersistenceService.populateNewQuestData(quest, data);
            repeatingQuest.addQuestData(quest.getId(), new QuestData(quest));
        }

        data.put("/repeatingQuests/" + repeatingQuest.getId(), repeatingQuest);
        getPlayerReference().updateChildren(data);
    }

    @Override
    public void update(RepeatingQuest repeatingQuest) {
        Map<String, Object> data = new HashMap<>();
        populateUpdateRepeatingQuest(repeatingQuest, data);
        getPlayerReference().updateChildren(data);
    }

    private void populateUpdateRepeatingQuest(RepeatingQuest repeatingQuest, Map<String, Object> data) {
        populateRepeatingQuestChallenge(repeatingQuest, data);

        for (String questId : repeatingQuest.getQuestsData().keySet()) {
            QuestData questData = repeatingQuest.getQuestsData().get(questId);
            if (repeatingQuest.getPreviousChallengeId() != null) {
                data.put("/challenges/" + repeatingQuest.getPreviousChallengeId() + "/questsData/" + questId, null);
            }
            String challengeId = repeatingQuest.getChallengeId();
            if (challengeId != null) {
                data.put("/challenges/" + challengeId + "/questsData/" + questId, questData);
            }
            if (questData.getScheduledDate() == null) {
                data.put("/inboxQuests/" + questId + "/challengeId", challengeId);
            } else {
                data.put("/dayQuests/" + questData.getScheduledDate() + "/" + questId + "/challengeId", challengeId);
            }
            data.put("/quests/" + questId + "/challengeId", challengeId);
        }
        data.put("/repeatingQuests/" + repeatingQuest.getId(), repeatingQuest);
    }

    @Override
    public void updateChallengeId(List<RepeatingQuest> repeatingQuests) {
        Map<String, Object> data = new HashMap<>();
        for (RepeatingQuest repeatingQuest : repeatingQuests) {
            populateUpdateRepeatingQuest(repeatingQuest, data);
        }
        getPlayerReference().updateChildren(data);
    }

    @Override
    public void saveScheduledRepeatingQuests(Map<RepeatingQuest, List<Quest>> repeatingQuestToScheduledQuests) {
        Map<String, Object> data = new HashMap<>();
        for (Map.Entry<RepeatingQuest, List<Quest>> entry : repeatingQuestToScheduledQuests.entrySet()) {
            RepeatingQuest repeatingQuest = entry.getKey();
            List<Quest> quests = entry.getValue();
            populateUpdateRepeatingQuest(data, repeatingQuest, quests);
        }
        getPlayerReference().updateChildren(data);
    }

    @Override
    public void save(Map<RepeatingQuest, List<Quest>> repeatingQuestToScheduledQuests) {
        Map<String, Object> data = new HashMap<>();
        for (Map.Entry<RepeatingQuest, List<Quest>> entry : repeatingQuestToScheduledQuests.entrySet()) {
            RepeatingQuest repeatingQuest = entry.getKey();
            List<Quest> quests = entry.getValue();
            populateNewRepeatingQuest(data, repeatingQuest, quests);
        }
        getPlayerReference().updateChildren(data);
    }

    private void populateNewRepeatingQuest(Map<String, Object> data, RepeatingQuest repeatingQuest, List<Quest> quests) {
        DatabaseReference rqRef = getCollectionReference().push();
        repeatingQuest.setId(rqRef.getKey());
        for (Quest q : quests) {
            q.setRepeatingQuestId(rqRef.getKey());
            questPersistenceService.populateNewQuestData(q, data);
            repeatingQuest.addQuestData(q.getId(), new QuestData(q));
        }
        if (!StringUtils.isEmpty(repeatingQuest.getChallengeId())) {
            data.put("/challenges/" + repeatingQuest.getChallengeId() + "/repeatingQuestIds/" + repeatingQuest.getId(), true);
            data.put("/challenges/" + repeatingQuest.getChallengeId() + "/challengeRepeatingQuests/" + repeatingQuest.getId(), repeatingQuest);
        }
        data.put("/repeatingQuests/" + repeatingQuest.getId(), repeatingQuest);
    }

    private void populateUpdateRepeatingQuest(Map<String, Object> data, RepeatingQuest repeatingQuest, List<Quest> quests) {
        for (Quest q : quests) {
            q.setRepeatingQuestId(repeatingQuest.getId());
            questPersistenceService.populateNewQuestData(q, data);
            repeatingQuest.addQuestData(q.getId(), new QuestData(q));
        }
        if (!StringUtils.isEmpty(repeatingQuest.getChallengeId())) {
            data.put("/challenges/" + repeatingQuest.getChallengeId() + "/repeatingQuestIds/" + repeatingQuest.getId(), true);
            data.put("/challenges/" + repeatingQuest.getChallengeId() + "/challengeRepeatingQuests/" + repeatingQuest.getId(), repeatingQuest);
        }
        data.put("/repeatingQuests/" + repeatingQuest.getId(), repeatingQuest);
    }

    private void populateRepeatingQuestChallenge(RepeatingQuest repeatingQuest, Map<String, Object> data) {
        if (repeatingQuest.getPreviousChallengeId() != null) {
            String challengeId = repeatingQuest.getPreviousChallengeId();
            data.put("/challenges/" + challengeId + "/repeatingQuestIds/" + repeatingQuest.getId(), null);
            data.put("/challenges/" + challengeId + "/challengeRepeatingQuests/" + repeatingQuest.getId(), null);
        }

        if (repeatingQuest.getChallengeId() != null) {
            String challengeId = repeatingQuest.getChallengeId();
            // @TODO check if repeating quest is complete
            data.put("/challenges/" + challengeId + "/repeatingQuestIds/" + repeatingQuest.getId(), false);
            data.put("/challenges/" + challengeId + "/challengeRepeatingQuests/" + repeatingQuest.getId(), repeatingQuest);
        }
    }

    @NonNull
    private Predicate<RepeatingQuest> createActiveRepeatingQuestPredicate() {
        return repeatingQuest -> repeatingQuest.getRecurrence().getDtendDate() == null
                || repeatingQuest.getRecurrence().getDtendDate().getTime() >= toStartOfDayUTC(LocalDate.now()).getTime();
    }
}
