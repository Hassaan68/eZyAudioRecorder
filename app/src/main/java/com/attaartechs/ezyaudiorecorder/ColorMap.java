package com.attaartechs.ezyaudiorecorder;

import com.attaartechs.ezyaudiorecorder.data.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ColorMap {

	private static ColorMap singleton;

	public static ColorMap getInstance(Prefs prefs) {
		if (singleton == null) {
			singleton = new ColorMap(prefs);
		}
		return singleton;
	}

	private static final int THEME_BLACK = 1;
	private static final int THEME_TEAL = 2;
	private static final int THEME_BLUE = 3;
	private static final int THEME_PURPLE = 4;
	private static final int THEME_PINK = 5;
	private static final int THEME_DEEP_ORANGE = 6;
	private static final int THEME_RED = 7;
	private static final int THEME_BROWN = 8;

	private int appThemeResource = 0;
	private int primaryColorRes = R.color.md_blue_700;
	private int playbackPanelBackground = R.drawable.panel_amber;
	private int selected;
	private List<OnThemeColorChangeListener> onThemeColorChangeListeners;
	private Prefs prefs;

	private ColorMap(Prefs prefs) {

		onThemeColorChangeListeners = new ArrayList<>();
		this.prefs = prefs;
		if (prefs.isFirstRun()) {
			selected = THEME_BLUE;
		} else {
			selected = prefs.getThemeColor();
		}
		init(selected);
	}

	private void init(int color) {
		if (color < 1 || color > 8) {
			color = new Random().nextInt(8);
		}
		switch (color) {
			case THEME_BLACK:
				appThemeResource = R.style.AppTheme_Black;
				primaryColorRes = R.color.md_black_1000;
				playbackPanelBackground = R.drawable.panel_red;
				break;
			case THEME_TEAL:
				appThemeResource = R.style.AppTheme_Teal;
				primaryColorRes = R.color.md_teal_700;
				playbackPanelBackground = R.drawable.panel_green;
				break;
			case THEME_PURPLE:
				appThemeResource = R.style.AppTheme_Purple;
				primaryColorRes = R.color.md_deep_purple_700;
				playbackPanelBackground = R.drawable.panel_pink;
				break;
			case THEME_PINK:
				appThemeResource = R.style.AppTheme_Pink;
				primaryColorRes = R.color.md_pink_800;
				playbackPanelBackground = R.drawable.panel_purple;
				break;
			case THEME_DEEP_ORANGE:
				appThemeResource = R.style.AppTheme_DeepOrange;
				primaryColorRes = R.color.md_deep_orange_800;
				playbackPanelBackground = R.drawable.panel_yellow;
				break;
			case THEME_RED:
				appThemeResource = R.style.AppTheme_Red;
				primaryColorRes = R.color.md_red_700;
				playbackPanelBackground = R.drawable.panel_purple_light;
				break;
			case THEME_BROWN:
				appThemeResource = R.style.AppTheme_Brown;
				primaryColorRes = R.color.md_brown_700;
				playbackPanelBackground = R.drawable.panel_deep_orange;
				break;
			case THEME_BLUE:
			default:
				primaryColorRes = R.color.md_blue_700;
				appThemeResource = R.style.AppTheme;
				playbackPanelBackground = R.drawable.panel_amber;
		}
	}

	public void updateColorMap(int num) {
		int ondSelected = selected;
		selected = num;
		if (ondSelected != selected) {
			prefs.setAppThemeColor(selected);
			init(selected);
			onThemeColorChange(selected);
		}
	}

	public int getSelected() {
		return selected;
	}

	public int getAppThemeResource() {
		return appThemeResource;
	}

	public int getPrimaryColorRes() {
		return primaryColorRes;
	}

	public int getPlaybackPanelBackground() {
		return playbackPanelBackground;
	}

	public int[] getColorResources() {
		return new int[] {
				R.color.transparent,
				R.color.md_black_1000,
				R.color.md_teal_700,
				R.color.md_blue_700,
				R.color.md_deep_purple_700,
				R.color.md_pink_800,
				R.color.md_deep_orange_800,
				R.color.md_red_700,
				R.color.md_brown_700
		};
	}

	public void addOnThemeColorChangeListener(OnThemeColorChangeListener onThemeColorChangeListener) {
		this.onThemeColorChangeListeners.add(onThemeColorChangeListener);
	}

	public void removeOnThemeColorChangeListener(OnThemeColorChangeListener onThemeColorChangeListener) {
		this.onThemeColorChangeListeners.remove(onThemeColorChangeListener);
	}

	public void onThemeColorChange(int pos) {
		for (int i = 0; i < onThemeColorChangeListeners.size(); i++) {
			onThemeColorChangeListeners.get(i).onThemeColorChange(pos);
		}
	}

	public interface OnThemeColorChangeListener {
		void onThemeColorChange(int pos);
	}
}
