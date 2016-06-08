package org.wordpress.android.ui.people;

import android.app.Fragment;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.wordpress.android.R;
import org.wordpress.android.datasets.PeopleTable;
import org.wordpress.android.models.FilterCriteria;
import org.wordpress.android.models.PeopleListFilter;
import org.wordpress.android.models.Person;
import org.wordpress.android.ui.EmptyViewMessageType;
import org.wordpress.android.ui.FilteredRecyclerView;
import org.wordpress.android.ui.prefs.AppPrefs;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.GravatarUtils;
import org.wordpress.android.util.NetworkUtils;
import org.wordpress.android.util.StringUtils;
import org.wordpress.android.widgets.WPNetworkImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PeopleListFragment extends Fragment {
    private static final String ARG_LOCAL_TABLE_BLOG_ID = "local_table_blog_id";

    private int mLocalTableBlogID;
    private OnPersonSelectedListener mOnPersonSelectedListener;
    private OnFetchPeopleListener mOnFetchPeopleListener;

    private FilteredRecyclerView mFilteredRecyclerView;
    private PeopleListFilter mPeopleListFilter;

    public static PeopleListFragment newInstance(int localTableBlogID) {
        PeopleListFragment peopleListFragment = new PeopleListFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(ARG_LOCAL_TABLE_BLOG_ID, localTableBlogID);
        peopleListFragment.setArguments(bundle);
        return peopleListFragment;
    }

    public void setOnPersonSelectedListener(OnPersonSelectedListener listener) {
        mOnPersonSelectedListener = listener;
    }

    public void setOnFetchPeopleListener(OnFetchPeopleListener listener) {
        mOnFetchPeopleListener = listener;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mOnPersonSelectedListener = null;
        mOnFetchPeopleListener = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.people_list_fragment, container, false);

        mFilteredRecyclerView = (FilteredRecyclerView) rootView.findViewById(R.id.filtered_recycler_view);
        mFilteredRecyclerView.addItemDecoration(new PeopleItemDecoration(getActivity(), R.drawable.people_list_divider));
        mFilteredRecyclerView.setLogT(AppLog.T.PEOPLE);
        mFilteredRecyclerView.setSwipeToRefreshEnabled(false);

        mFilteredRecyclerView.setFilterListener(new FilteredRecyclerView.FilterListener() {
            @Override
            public List<FilterCriteria> onLoadFilterCriteriaOptions(boolean refresh) {
                ArrayList<FilterCriteria> list = new ArrayList<>();
                Collections.addAll(list, PeopleListFilter.values());
                return list;
            }

            @Override
            public void onLoadFilterCriteriaOptionsAsync(FilteredRecyclerView.FilterCriteriaAsyncLoaderListener listener, boolean refresh) {
                // no-op
            }

            @Override
            public FilterCriteria onRecallSelection() {
                mPeopleListFilter = AppPrefs.getPeopleListFilter();
                return mPeopleListFilter;
            }

            @Override
            public void onLoadData() {
                updatePeople(false);
            }

            @Override
            public void onFilterSelected(int position, FilterCriteria criteria) {
                mPeopleListFilter = (PeopleListFilter) criteria;
                AppPrefs.setPeopleListFilter(mPeopleListFilter);
            }

            @Override
            public String onShowEmptyViewMessage(EmptyViewMessageType emptyViewMsgType) {
                return null;
            }

            @Override
            public void onShowCustomEmptyView(EmptyViewMessageType emptyViewMsgType) {

            }
        });

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mLocalTableBlogID = getArguments().getInt(ARG_LOCAL_TABLE_BLOG_ID);

        // refresh the first page to serve fresh data
        updatePeople(false);
    }

    @Override
    public void onResume() {
        super.onResume();

        refreshPeopleList();
    }

    public void refreshPeopleList() {
        if (!isAdded()) return;

        List<Person> peopleList = PeopleTable.getPeople(mLocalTableBlogID);
        PeopleAdapter peopleAdapter = (PeopleAdapter) mFilteredRecyclerView.getAdapter();
        if (peopleAdapter == null) {
            peopleAdapter = new PeopleAdapter(getActivity(), peopleList);
            mFilteredRecyclerView.setAdapter(peopleAdapter);
        } else {
            peopleAdapter.setPeopleList(peopleList);
        }
        if (peopleList != null && !peopleList.isEmpty()) {
            mFilteredRecyclerView.hideEmptyView();
        }
    }

    private void updatePeople(boolean loadMore) {
        if (!NetworkUtils.isNetworkAvailable(getActivity())) {
            mFilteredRecyclerView.updateEmptyView(EmptyViewMessageType.NETWORK_ERROR);
            mFilteredRecyclerView.setRefreshing(false);
            return;
        }

        mFilteredRecyclerView.updateEmptyView(EmptyViewMessageType.LOADING);

        if (mOnFetchPeopleListener != null) {
            if (loadMore) {
                mOnFetchPeopleListener.onFetchMorePeople();
            } else {
                mOnFetchPeopleListener.onFetchFirstPage();
            }
        }
    }

    /*
    * show/hide progress bar which appears at the bottom of the activity when loading more people
    */
    public void showLoadingProgress(boolean showProgress) {
        if (isAdded() && mFilteredRecyclerView != null) {
            if (showProgress) {
                mFilteredRecyclerView.showLoadingProgress();
            } else {
                mFilteredRecyclerView.hideLoadingProgress();
            }
        }
    }

    // Container Activity must implement this interface
    public interface OnPersonSelectedListener {
        void onPersonSelected(Person person);
    }

    public interface OnFetchPeopleListener {
        void onFetchFirstPage();
        void onFetchMorePeople();
    }

    public class PeopleAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final LayoutInflater mInflater;
        private List<Person> mPeopleList;
        private int mAvatarSz;

        public PeopleAdapter(Context context, List<Person> peopleList) {
            mAvatarSz = context.getResources().getDimensionPixelSize(R.dimen.people_avatar_sz);
            mInflater = LayoutInflater.from(context);
            mPeopleList = peopleList;
            setHasStableIds(true);
        }

        public void setPeopleList(List<Person> peopleList) {
            mPeopleList = peopleList;
            notifyDataSetChanged();
        }

        public Person getPerson(int position) {
            if (mPeopleList == null) {
                return null;
            }
            return mPeopleList.get(position);
        }

        @Override
        public int getItemCount() {
            if (mPeopleList == null) {
                return 0;
            }
            return mPeopleList.size();
        }

        @Override
        public long getItemId(int position) {
            Person person = getPerson(position);
            if (person == null) {
                return -1;
            }
            return person.getPersonID();
        }

        @Override
        public PeopleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = mInflater.inflate(R.layout.people_list_row, parent, false);

            return new PeopleViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            PeopleViewHolder peopleViewHolder = (PeopleViewHolder) holder;
            final Person person = getPerson(position);

            if (person != null) {
                String avatarUrl = GravatarUtils.fixGravatarUrl(person.getAvatarUrl(), mAvatarSz);
                peopleViewHolder.imgAvatar.setImageUrl(avatarUrl, WPNetworkImageView.ImageType.AVATAR);
                peopleViewHolder.txtDisplayName.setText(person.getDisplayName());
                peopleViewHolder.txtUsername.setText(String.format("@%s", person.getUsername()));
                peopleViewHolder.txtRole.setText(StringUtils.capitalize(person.getRole()));
            }

            // end of list is reached
            if (position == getItemCount() - 1) {
                updatePeople(true);
            }
        }

        public class PeopleViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            private final WPNetworkImageView imgAvatar;
            private final TextView txtDisplayName;
            private final TextView txtUsername;
            private final TextView txtRole;

            public PeopleViewHolder(View view) {
                super(view);
                imgAvatar = (WPNetworkImageView) view.findViewById(R.id.person_avatar);
                txtDisplayName = (TextView) view.findViewById(R.id.person_display_name);
                txtUsername = (TextView) view.findViewById(R.id.person_username);
                txtRole = (TextView) view.findViewById(R.id.person_role);

                itemView.setOnClickListener(this);
            }

            @Override
            public void onClick(View v) {
                if (mOnPersonSelectedListener != null) {
                    Person person = getPerson(getAdapterPosition());
                    mOnPersonSelectedListener.onPersonSelected(person);
                }
            }
        }
    }

    // Taken from http://stackoverflow.com/a/27037230
    private class PeopleItemDecoration extends RecyclerView.ItemDecoration {
        private Drawable mDivider;

        // use a custom drawable
        public PeopleItemDecoration(Context context, int resId) {
            mDivider = ContextCompat.getDrawable(context, resId);
        }

        @Override
        public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
            int left = parent.getPaddingLeft();
            int right = parent.getWidth() - parent.getPaddingRight();

            int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);

                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();

                int top = child.getBottom() + params.bottomMargin;
                int bottom = top + mDivider.getIntrinsicHeight();

                mDivider.setBounds(left, top, right, bottom);
                mDivider.draw(c);
            }
        }
    }
}
