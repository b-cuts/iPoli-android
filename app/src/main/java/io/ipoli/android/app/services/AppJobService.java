package io.ipoli.android.app.services;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.squareup.otto.Bus;

import java.util.Set;

import javax.inject.Inject;

import io.ipoli.android.Constants;
import io.ipoli.android.app.App;
import io.ipoli.android.app.net.APIService;
import io.ipoli.android.app.net.AuthProvider;
import io.ipoli.android.app.net.JsonRequestBodyBuilder;
import io.ipoli.android.app.services.events.SyncCompleteEvent;
import io.ipoli.android.app.utils.DateUtils;
import io.ipoli.android.app.utils.LocalStorage;
import io.ipoli.android.player.Player;
import io.ipoli.android.player.persistence.PlayerPersistenceService;
import io.ipoli.android.quest.data.Quest;
import io.ipoli.android.quest.data.RecurrentQuest;
import io.ipoli.android.quest.persistence.QuestPersistenceService;
import io.ipoli.android.quest.persistence.RecurrentQuestPersistenceService;
import okhttp3.RequestBody;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 3/25/16.
 */
public class AppJobService extends JobService {

    @Inject
    PlayerPersistenceService playerPersistenceService;

    @Inject
    QuestPersistenceService questPersistenceService;

    @Inject
    RecurrentQuestPersistenceService recurrentQuestPersistenceService;

    @Inject
    APIService apiService;

    @Inject
    Gson gson;

    @Inject
    Bus eventBus;

    @Override
    public boolean onStartJob(JobParameters params) {
        App.getAppComponent(this).inject(this);

        getPlayer().flatMap(p -> Observable.concat(
                syncRemovedRecurrentQuests(p),
                syncRemovedQuests(p),
                syncRecurrentQuests(p),
                syncQuests(p),
                getRecurrentQuests(p),
                getScheduleForAWeekAhead(p)
        )).subscribe(res -> {
            Log.d("RxJava", "OnNext " + res);
        }, throwable -> {
            Log.e("RxJava", "Error", throwable);
            jobFinished(params, true);
        }, () -> {
            eventBus.post(new SyncCompleteEvent());
            Log.d("RxJava", "Sync Job finished");
            jobFinished(params, false);
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d("RxJava", "Stopping job" + params);
        return false;
    }

    private Observable<Player> getPlayer() {
        return playerPersistenceService.find().concatMap(player -> {
            if (player == null) {
                return createNewPlayer();
            }
            if (player.needsSyncWithRemote()) {
                return syncPlayer(player);
            }
            return Observable.just(player);
        });
    }

    private Observable<Player> syncPlayer(Player p) {
        RequestBody requestBody = new JsonRequestBodyBuilder().param("uid", p.getId()).param("provider", AuthProvider.ANONYMOUS).build();
        return apiService.createPlayer(requestBody).compose(applyAPISchedulers()).concatMap(sp -> {
            playerPersistenceService.updateId(p, sp.getId());
            sp.setSyncedWithRemote();
            sp.setRemoteObject();
            return playerPersistenceService.save(sp, false);
        });
    }

    private Observable<Void> syncRemovedRecurrentQuests(Player player) {
        return Observable.just(LocalStorage.of(getApplicationContext()).readStringSet(Constants.KEY_REMOVED_RECURRENT_QUESTS))
                .concatMapIterable(ids -> ids)
                .concatMap(id -> apiService.deleteRecurrentQuest(id, player.getId()).compose(applyAPISchedulers())
                        .concatMap(res -> {
                            LocalStorage localStorage = LocalStorage.of(getApplicationContext());
                            Set<String> removedQuests = localStorage.readStringSet(Constants.KEY_REMOVED_RECURRENT_QUESTS);
                            removedQuests.remove(id);
                            localStorage.saveStringSet(Constants.KEY_REMOVED_RECURRENT_QUESTS, removedQuests);
                            return Observable.<Void>empty();
                        }));
    }

    private Observable<Void> syncRemovedQuests(Player player) {
        return Observable.just(LocalStorage.of(getApplicationContext()).readStringSet(Constants.KEY_REMOVED_QUESTS))
                .concatMapIterable(ids -> ids)
                .concatMap(id -> apiService.deleteQuest(id, player.getId()).compose(applyAPISchedulers())
                        .concatMap(res -> {
                            LocalStorage localStorage = LocalStorage.of(getApplicationContext());
                            Set<String> removedQuests = localStorage.readStringSet(Constants.KEY_REMOVED_QUESTS);
                            removedQuests.remove(id);
                            localStorage.saveStringSet(Constants.KEY_REMOVED_QUESTS, removedQuests);
                            return Observable.<Void>empty();
                        }));
    }

    private Observable<Player> createNewPlayer() {
        Player defaultPlayer = new Player(Constants.DEFAULT_PLAYER_EXPERIENCE, Constants.DEFAULT_PLAYER_LEVEL, Constants.DEFAULT_PLAYER_AVATAR);
        return playerPersistenceService.save(defaultPlayer, false).concatMap(this::syncPlayer);
    }

    private Observable<Quest> syncQuests(Player player) {
        return questPersistenceService.findAllWhoNeedSyncWithRemote().concatMapIterable(quests -> quests).concatMap(q -> {
            JsonObject qJson = (JsonObject) gson.toJsonTree(q);
            if (!q.isRemoteObject()) {
                String id = null;
                qJson.addProperty("id", id);
            }
            RequestBody requestBody = new JsonRequestBodyBuilder().param("data", qJson).param("player_id", player.getId()).build();
            return apiService.updateQuest(requestBody).compose(applyAPISchedulers()).concatMap(sq -> {
                if (!q.isRemoteObject()) {
                    questPersistenceService.updateId(q, sq.getId());
                }
                sq.setSyncedWithRemote();
                sq.setRemoteObject();
                return questPersistenceService.save(sq, false);
            });
        });
    }

    private Observable<RecurrentQuest> syncRecurrentQuests(Player player) {
        return recurrentQuestPersistenceService.findAllWhoNeedSyncWithRemote().flatMapIterable(recurrentQuests -> recurrentQuests).flatMap(rq -> {
            if (rq.isRemoteObject()) {
                JsonObject qJson = (JsonObject) gson.toJsonTree(rq);
                qJson.addProperty("id", rq.getId());
                RequestBody requestBody = new JsonRequestBodyBuilder().param("data", qJson).param("player_id", player.getId()).build();
                return apiService.updateRecurrentQuest(requestBody, rq.getId()).compose(applyAPISchedulers()).flatMap(sq -> {
                    updateRecurrentQuest(sq, rq);
                    return recurrentQuestPersistenceService.save(sq, false);
                });
            } else {
                JsonObject data = new JsonObject();
                JsonObject qJson = (JsonObject) gson.toJsonTree(rq);
                data.addProperty("text", qJson.get("raw_text").getAsString());
                data.addProperty("context", qJson.get("context").getAsString());
                data.addProperty("created_at", qJson.get("created_at").getAsString());
                data.addProperty("updated_at", qJson.get("updated_at").getAsString());
                RequestBody requestBody = new JsonRequestBodyBuilder().param("data", data).param("player_id", player.getId()).build();
                return apiService.createRecurrentQuestFromText(requestBody).compose(applyAPISchedulers()).flatMap(sq -> {
                    updateRecurrentQuest(sq, rq);
                    return recurrentQuestPersistenceService.save(sq, false);
                });
            }
        });
    }

    private void updateRecurrentQuest(RecurrentQuest serverQuest, RecurrentQuest localQuest) {
        if (!localQuest.isRemoteObject()) {
            recurrentQuestPersistenceService.updateId(localQuest, serverQuest.getId());
        }
        serverQuest.setSyncedWithRemote();
        serverQuest.setRemoteObject();
    }

    private Observable<RecurrentQuest> getRecurrentQuests(Player player) {
        return apiService.getRecurrentQuests(player.getId())
                .compose(applyAPISchedulers()).flatMapIterable(recurrentQuests -> recurrentQuests)
                .flatMap(sq -> recurrentQuestPersistenceService.findById(sq.getId()).flatMap(rq -> {
                    if (rq != null && sq.getUpdatedAt().getTime() <= rq.getUpdatedAt().getTime()) {
                        return Observable.just(rq);
                    }

                    if (rq == null) {
                        sq.setRemoteObject();
                        sq.setSyncedWithRemote();
                        return recurrentQuestPersistenceService.save(sq, false);
                    }
                    updateRecurrentQuest(sq, rq);
                    return recurrentQuestPersistenceService.save(sq, false);
                }));
    }

    private Observable<Quest> getScheduleForAWeekAhead(Player player) {
        return Observable.just(DateUtils.getNext7Days()).concatMapIterable(dates -> dates)
                .concatMap(date -> apiService.getSchedule(date, player.getId()).compose(applyAPISchedulers())).concatMapIterable(quests -> quests)
                .concatMap(sq -> questPersistenceService.findById(sq.getId()).concatMap(q -> {
                    if (q != null && sq.getUpdatedAt().getTime() <= q.getUpdatedAt().getTime()) {
                        return Observable.just(q);
                    }

                    sq.setSyncedWithRemote();
                    sq.setRemoteObject();

                    if (q != null && !q.isRemoteObject()) {
                        questPersistenceService.updateId(q, sq.getId());
                    }

                    return questPersistenceService.save(sq, false);
                }));
    }

    private <T> Observable.Transformer<T, T> applyAPISchedulers() {
        return observable -> observable.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }
}