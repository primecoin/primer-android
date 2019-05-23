/*
 * Copyright 2014 http://Bither.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.primer.activity.hot;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ToggleButton;

import org.primer.PrimerSetting;
import org.primer.R;
import org.primer.primerj.core.AddressManager;
import org.primer.primerj.crypto.PasswordSeed;
import org.primer.fragment.hot.AddAddressHotHDAccountFragment;
import org.primer.fragment.hot.AddAddressHotHDAccountViewFragment;
import org.primer.fragment.hot.AddAddressHotOtherFragment;
import org.primer.fragment.hot.AddAddressPrivateKeyFragment;
import org.primer.ui.base.AddPrivateKeyActivity;
import org.primer.util.StringUtil;

import java.util.ArrayList;

public class AddHotAddressActivity extends AddPrivateKeyActivity {
    private ToggleButton tbtnHDAccount;
    private ToggleButton tbtnOther;
    private ViewPager pager;
    private ImageButton ibtnCancel;

    private AddAddressHotHDAccountFragment hdAccountFragment;
    private AddAddressHotHDAccountViewFragment hdAccountViewFragment;
    private AddAddressHotOtherFragment otherFragment;

    private boolean shouldSuggestCheck = false;

    @Override
    protected void onCreate(Bundle arg0) {
        super.onCreate(arg0);
        setContentView(R.layout.activity_add_hot_address);
        if (!PasswordSeed.hasPasswordSeed()) {
            shouldSuggestCheck = true;
        } else {
            shouldSuggestCheck = false;
        }
        initView();
    }

    private void initView() {
        tbtnHDAccount = (ToggleButton) findViewById(R.id.tbtn_hd_account);
        tbtnOther = (ToggleButton) findViewById(R.id.tbtn_other);
        pager = (ViewPager) findViewById(R.id.pager);
        ibtnCancel = (ImageButton) findViewById(R.id.ibtn_cancel);
        tbtnHDAccount.setOnClickListener(new IndicatorClick(0));
        tbtnOther.setOnClickListener(new IndicatorClick(1));
        ibtnCancel.setOnClickListener(cancelClick);
        pager.setAdapter(adapter);
        pager.setOnPageChangeListener(pageChange);
        pager.setCurrentItem(0);
        tbtnHDAccount.setChecked(true);
    }

    private Fragment getHDAccountFragment() {
        if (AddressManager.getInstance().hasHDAccountHot()) {
            if (hdAccountViewFragment == null) {
                hdAccountViewFragment = new AddAddressHotHDAccountViewFragment();
            }
            return hdAccountViewFragment;
        }
        if (hdAccountFragment == null) {
            hdAccountFragment = new AddAddressHotHDAccountFragment();
        }
        return hdAccountFragment;
    }

    private Fragment getOtherFragment() {
        if (otherFragment == null) {
            otherFragment = new AddAddressHotOtherFragment();
        }
        return otherFragment;
    }

    private OnClickListener cancelClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    };

    public void save() {
        Fragment f = getActiveFragment();
        if (f != null && f instanceof AddAddress) {
            AddAddress a = (AddAddress) f;
            ArrayList<String> addresses = a.getAddresses();
            Intent intent = new Intent();
            intent.putExtra(PrimerSetting.INTENT_REF.ADDRESS_POSITION_PASS_VALUE_TAG, addresses);
            if (f instanceof AddAddressPrivateKeyFragment) {
                intent.putExtra(PrimerSetting.INTENT_REF.ADD_PRIVATE_KEY_SUGGEST_CHECK_TAG,
                        shouldSuggestCheck);
            }
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    }

    private class IndicatorClick implements OnClickListener {
        private int position;

        public IndicatorClick(int position) {
            this.position = position;
        }

        @Override
        public void onClick(View v) {
            initPosition(position);
        }
    }

    private void initPosition(int position) {
        if (position == 0) {
            pager.setCurrentItem(0, true);
            tbtnHDAccount.setChecked(true);
            tbtnOther.setChecked(false);
        } else if (position == 1) {
            pager.setCurrentItem(1, true);
            tbtnHDAccount.setChecked(false);
            tbtnOther.setChecked(true);
        }
    }

    private FragmentPagerAdapter adapter = new FragmentPagerAdapter(getSupportFragmentManager()) {

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public Fragment getItem(int index) {
            if (index == 0) {
                return getHDAccountFragment();
            }
            if (index == 1) {
                return getOtherFragment();
            }
            return null;
        }
    };

    private OnPageChangeListener pageChange = new OnPageChangeListener() {

        @Override
        public void onPageSelected(int index) {
            if (index == 0) {
                tbtnHDAccount.setChecked(true);
                tbtnOther.setChecked(false);
            } else {
                tbtnHDAccount.setChecked(false);
                tbtnOther.setChecked(true);
            }
        }

        @Override
        public void onPageScrolled(int arg0, float arg1, int arg2) {

        }

        @Override
        public void onPageScrollStateChanged(int arg0) {

        }
    };

    public Fragment getFragmentAtIndex(int i) {
        String str = StringUtil.makeFragmentName(this.pager.getId(), i);
        return getSupportFragmentManager().findFragmentByTag(str);
    }

    public Fragment getActiveFragment() {
        Fragment localFragment = null;
        if (this.pager == null) {
            return localFragment;
        }
        localFragment = getFragmentAtIndex(pager.getCurrentItem());
        return localFragment;
    }

    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.slide_out_bottom);
    }

    public static interface AddAddress {
        public ArrayList<String> getAddresses();
    }
}