package dev.android.dynamicisland.settings;

import static org.junit.Assert.*;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import dev.android.dynamicisland.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="ko-rKR-w360dp-h800dp-night-xxhdpi")
public class IModeSettingsTest {
    @Test public void defaultsToExistingStyleAndSelectsMutuallyExclusiveStylesThroughTheRealSettingsUi() {
        try(var controller=Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity a=controller.get();SharedPreferences prefs=ReflectionHelpers.getField(a,"prefs");
            prefs.edit().clear().commit();ModuleSettings.configurePreview(prefs);
            assertFalse(ModuleSettings.iMode());
            a.openSetting(SettingsSearchCatalog.Route.APPEARANCE,R.string.anim_title,0);
            SettingsSearchCatalog catalog=new SettingsSearchCatalog(a);
            var result=catalog.rows.get(catalog.engine.search("iMotion",5).get(0).id);
            assertEquals(SettingsSearchCatalog.Route.APPEARANCE,result.route);
            choose(a,2);assertTrue(prefs.getBoolean(ModuleSettings.KEY_I_MODE,false));assertTrue(ModuleSettings.iMode());
            assertFalse(prefs.getBoolean(ModuleSettings.KEY_LEGACY_ANIMATION,false));
            choose(a,1);assertFalse(ModuleSettings.iMode());assertTrue(ModuleSettings.legacyAnimation());
            choose(a,0);assertFalse(ModuleSettings.iMode());assertFalse(ModuleSettings.legacyAnimation());
            prefs.edit().clear().commit();ModuleSettings.configurePreview(prefs);
        }
    }
    @Test public void oneDesignControlsAppearOnlyForThatStyleAndKeepTheirValues() {
        try (var controller = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity activity = controller.get();
            SharedPreferences prefs = ReflectionHelpers.getField(activity, "prefs");
            try {
                prefs.edit().clear()
                        .putBoolean(ModuleSettings.KEY_WIDER_ALBUM_COVER, true)
                        .putInt(ModuleSettings.KEY_APP_ICON_ZOOM, 125).commit();
                activity.openSetting(SettingsSearchCatalog.Route.APPEARANCE, R.string.island_style, 0);
                String zoom = activity.getString(R.string.app_icon_zoom);
                String cover = activity.getString(R.string.wider_album_cover);

                assertTrue(styleRow(activity, R.string.style_pill).isSelected());
                assertFalse(styleRow(activity, R.string.style_one_design).isSelected());
                assertNull(activity.page().findViewWithTag(zoom));
                assertNull(activity.page().findViewWithTag(cover));

                styleRow(activity, R.string.style_one_design).performClick();
                assertTrue(ModuleSettings.oneDesign());
                assertFalse(styleRow(activity, R.string.style_pill).isSelected());
                assertTrue(styleRow(activity, R.string.style_one_design).isSelected());
                assertNotNull(activity.page().findViewWithTag(zoom));
                assertNotNull(activity.page().findViewWithTag(cover));

                styleRow(activity, R.string.style_pill).performClick();
                assertFalse(ModuleSettings.oneDesign());
                assertNull(activity.page().findViewWithTag(zoom));
                assertNull(activity.page().findViewWithTag(cover));

                styleRow(activity, R.string.style_one_design).performClick();
                assertTrue(ModuleSettings.widerAlbumCover());
                assertEquals(125, ModuleSettings.appIconZoom());
            } finally {
                prefs.edit().clear().commit();
                ModuleSettings.configurePreview(prefs);
            }
        }
    }

    private View styleRow(SettingsActivity activity, int label) {
        ViewGroup page = activity.page();
        ViewGroup group = page.findViewWithTag(activity.getString(R.string.island_style));
        View animation = page.findViewWithTag(activity.getString(R.string.anim_title));
        assertNotNull(group);
        assertNotNull(animation);
        assertTrue(page.indexOfChild(group) < page.indexOfChild(animation));
        for (int i = 0; i < group.getChildCount(); i++) {
            View row = group.getChildAt(i);
            if (activity.getString(label).equals(row.getContentDescription())) return row;
        }
        throw new AssertionError("Missing style option " + activity.getString(label));
    }

    /** Taps one row of the animation radio list; the live preview plays the result. */
    private void choose(SettingsActivity activity,int index) {
        android.view.ViewGroup card=(android.view.ViewGroup)activity.page().findViewWithTag(activity.getString(R.string.anim_title));
        assertNotNull(card);
        java.util.List<android.view.View> rows=new java.util.ArrayList<>();
        for(int i=0;i<card.getChildCount();i++)if(card.getChildAt(i).isClickable())rows.add(card.getChildAt(i));
        assertEquals(3,rows.size());
        rows.get(index).performClick();
    }
}
