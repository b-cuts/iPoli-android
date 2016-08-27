package io.ipoli.android.app.modules;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.ipoli.android.app.utils.LocalStorage;
import io.ipoli.android.quest.generators.CoinsRewardGenerator;
import io.ipoli.android.quest.generators.ExperienceRewardGenerator;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 8/24/16.
 */
@Module
public class RewardGeneratorModule {

    @Provides
    @Singleton
    public ExperienceRewardGenerator provideExperienceRewardGenerator(LocalStorage localStorage) {
        return new ExperienceRewardGenerator(localStorage);
    }

    @Provides
    @Singleton
    public CoinsRewardGenerator provideCoinsRewardGenerator(LocalStorage localStorage) {
        return new CoinsRewardGenerator(localStorage);
    }
}
