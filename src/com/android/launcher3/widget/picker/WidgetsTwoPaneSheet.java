/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.widget.picker;

import android.content.Context;
import android.graphics.Outline;
import android.os.Process;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.android.launcher3.R;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.recyclerview.ViewHolderBinder;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;

import java.util.Collections;
import java.util.List;

/**
 * Popup for showing the full list of available widgets with a two-pane layout.
 */
public class WidgetsTwoPaneSheet extends WidgetsFullSheet {

    private static final int PERSONAL_TAB = 0;
    private static final int WORK_TAB = 1;
    private static final String SUGGESTIONS_PACKAGE_NAME = "widgets_list_suggestions_entry";

    private LinearLayout mSuggestedWidgetsContainer;
    private WidgetsListHeader mSuggestedWidgetsHeader;
    private LinearLayout mRightPane;

    private FrameLayout mRightPaneScrollView;
    private WidgetsListTableViewHolderBinder mWidgetsListTableViewHolderBinder;

    private final ViewOutlineProvider mViewOutlineProviderRightPane = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRoundRect(
                    0,
                    0,
                    view.getMeasuredWidth(),
                    view.getMeasuredHeight() - getResources().getDimensionPixelSize(
                            R.dimen.widget_list_horizontal_margin_large_screen),
                    view.getResources().getDimensionPixelSize(
                            R.dimen.widget_list_top_bottom_corner_radius)
            );
        }
    };

    public WidgetsTwoPaneSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public WidgetsTwoPaneSheet(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void setupSheet() {
        // Set the header change listener in the adapter
        mAdapters.get(AdapterHolder.PRIMARY)
                .mWidgetsListAdapter.setHeaderChangeListener(getHeaderChangeListener());
        mAdapters.get(AdapterHolder.WORK)
                .mWidgetsListAdapter.setHeaderChangeListener(getHeaderChangeListener());
        mAdapters.get(AdapterHolder.SEARCH)
                .mWidgetsListAdapter.setHeaderChangeListener(getHeaderChangeListener());

        LayoutInflater layoutInflater = LayoutInflater.from(getContext());

        int contentLayoutRes = mHasWorkProfile ? R.layout.widgets_full_sheet_paged_view_large_screen
                : R.layout.widgets_full_sheet_recyclerview_large_screen;
        layoutInflater.inflate(contentLayoutRes, findViewById(R.id.recycler_view_container), true);

        setupViews();

        mWidgetsListTableViewHolderBinder =
                new WidgetsListTableViewHolderBinder(mActivityContext, layoutInflater, this, this);
        mRecommendedWidgetsTable = mContent.findViewById(R.id.recommended_widget_table);
        mRecommendedWidgetsTable.setWidgetCellLongClickListener(this);
        mRecommendedWidgetsTable.setWidgetCellOnClickListener(this);
        mHeaderTitle = mContent.findViewById(R.id.title);
        mRightPane = mContent.findViewById(R.id.right_pane);
        mRightPane.setOutlineProvider(mViewOutlineProviderRightPane);
        mRightPaneScrollView = mContent.findViewById(R.id.right_pane_scroll_view);
        mRightPaneScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        setupSuggestedWidgets(layoutInflater);
        onRecommendedWidgetsBound();
        onWidgetsBound();
        setUpEducationViewsIfNeeded();

        // Set the fast scroller as not visible for two pane layout.
        mFastScroller.setVisibility(GONE);
    }

    @Override
    protected void hideRecommendations() {
        super.hideRecommendations();
        mSuggestedWidgetsContainer.setVisibility(GONE);
    }

    private void setupSuggestedWidgets(LayoutInflater layoutInflater) {
        // Add suggested widgets.
        mSuggestedWidgetsContainer = mSearchScrollView.findViewById(R.id.suggestions_header);

        // Inflate the suggestions header.
        mSuggestedWidgetsHeader = (WidgetsListHeader) layoutInflater.inflate(
                R.layout.widgets_list_row_header_two_pane,
                mSuggestedWidgetsContainer,
                false);
        mSuggestedWidgetsHeader.setExpanded(true);

        PackageItemInfo packageItemInfo = new PackageItemInfo(
                /* packageName= */ SUGGESTIONS_PACKAGE_NAME,
                Process.myUserHandle()) {
            @Override
            public boolean usingLowResIcon() {
                return false;
            }
        };
        packageItemInfo.title = getContext().getString(R.string.suggested_widgets_header_title);
        WidgetsListHeaderEntry widgetsListHeaderEntry = WidgetsListHeaderEntry.create(
                        packageItemInfo,
                        getContext().getString(R.string.suggested_widgets_header_title),
                        mActivityContext.getPopupDataProvider().getRecommendedWidgets())
                .withWidgetListShown();

        mSuggestedWidgetsHeader.applyFromItemInfoWithIcon(widgetsListHeaderEntry);
        mSuggestedWidgetsHeader.setIcon(
                getContext().getDrawable(R.drawable.widget_suggestions_icon));
        mSuggestedWidgetsHeader.setOnClickListener(view -> {
            mSuggestedWidgetsHeader.setExpanded(true);
            resetExpandedHeaders();
            mRightPane.removeAllViews();
            mRightPane.addView(mRecommendedWidgetsTable);
        });
        mSuggestedWidgetsContainer.addView(mSuggestedWidgetsHeader);
    }

    @Override
    protected float getMaxTableHeight(float noWidgetsViewHeight) {
        return Float.MAX_VALUE;
    }

    @Override
    public void onActivePageChanged(int currentActivePage) {
        // if the current active page changes to personal or work we set suggestions
        // to be the selected widget
        if (currentActivePage == PERSONAL_TAB || currentActivePage == WORK_TAB) {
            mSuggestedWidgetsHeader.callOnClick();
        }

        super.onActivePageChanged(currentActivePage);
    }

    @Override
    protected void updateRecyclerViewVisibility(AdapterHolder adapterHolder) {
        // The first item is always an empty space entry. Look for any more items.
        boolean isWidgetAvailable = adapterHolder.mWidgetsListAdapter.hasVisibleEntries();

        mRightPane.setVisibility(isWidgetAvailable ? VISIBLE : GONE);

        super.updateRecyclerViewVisibility(adapterHolder);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSuggestedWidgetsContainer.setVisibility(VISIBLE);
    }

    @Override
    public void onSearchResults(List<WidgetsListBaseEntry> entries) {
        super.onSearchResults(entries);
        mAdapters.get(AdapterHolder.SEARCH).mWidgetsListAdapter.selectFirstHeaderEntry();
    }

    @Override
    protected boolean shouldScroll(MotionEvent ev) {
        return getPopupContainer().isEventOverView(mRightPaneScrollView, ev)
                ? mRightPaneScrollView.canScrollVertically(-1)
                : super.shouldScroll(ev);
    }

    @Override
    protected void setViewVisibilityBasedOnSearch(boolean isInSearchMode) {
        if (isInSearchMode) {
            mSuggestedWidgetsContainer.setVisibility(GONE);
        } else {
            mSuggestedWidgetsContainer.setVisibility(VISIBLE);
            mSuggestedWidgetsHeader.callOnClick();
        }
        super.setViewVisibilityBasedOnSearch(isInSearchMode);
    }

    @Override
    protected View getContentView() {
        return mRightPane;
    }

    private HeaderChangeListener getHeaderChangeListener() {
        return new HeaderChangeListener() {
            @Override
            public void onHeaderChanged(@NonNull PackageUserKey selectedHeader) {
                WidgetsListContentEntry contentEntry = mActivityContext.getPopupDataProvider()
                        .getSelectedAppWidgets(selectedHeader);

                if (contentEntry == null || mRightPane == null) {
                    return;
                }

                if (mSuggestedWidgetsHeader != null) {
                    mSuggestedWidgetsHeader.setExpanded(false);
                }
                WidgetsRowViewHolder widgetsRowViewHolder =
                        mWidgetsListTableViewHolderBinder.newViewHolder(mRightPane);
                mWidgetsListTableViewHolderBinder.bindViewHolder(widgetsRowViewHolder,
                        contentEntry,
                        ViewHolderBinder.POSITION_FIRST | ViewHolderBinder.POSITION_LAST,
                        Collections.EMPTY_LIST);
                widgetsRowViewHolder.mDataCallback = data -> {
                    mWidgetsListTableViewHolderBinder.bindViewHolder(widgetsRowViewHolder,
                            contentEntry,
                            ViewHolderBinder.POSITION_FIRST | ViewHolderBinder.POSITION_LAST,
                            Collections.singletonList(data));
                };
                mRightPane.removeAllViews();
                mRightPane.addView(widgetsRowViewHolder.itemView);
            }
        };
    }

    @Override
    protected boolean isTwoPane() {
        return true;
    }

    /**
     * This is a listener for when the selected header gets changed in the left pane.
     */
    public interface HeaderChangeListener {
        /**
         * Sets the right pane to have the widgets for the currently selected header from
         * the left pane.
         */
        void onHeaderChanged(@NonNull PackageUserKey selectedHeader);
    }
}
