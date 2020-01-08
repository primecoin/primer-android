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

package net.bither.activity.hot;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.format.DateUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.bither.NotificationAndroidImpl;
import net.bither.PrimerApplication;
import net.bither.PrimerSetting;
import net.bither.adapter.hot.HotFragmentPagerAdapter;
import net.bither.bitherj.core.Block;
import net.bither.bitherj.core.BlockChain;
import net.bither.ui.base.BaseFragmentActivity;
import net.bither.ui.base.DropdownMessage;
import net.bither.ui.base.SyncProgressView;
import net.bither.ui.base.TabButton;
import net.bither.ui.base.dialog.DialogFirstRunWarning;
import net.bither.ui.base.dialog.DialogGenerateAddressFinalConfirm;
import net.bither.ui.base.dialog.DialogProgress;
import net.bither.util.LogUtil;
import net.bither.util.NetworkUtil;
import net.bither.util.StringUtil;
import net.bither.util.UIUtil;
import net.bither.util.WalletUtils;

import net.bither.PrimerApplication;
import net.bither.PrimerSetting;
import net.bither.NotificationAndroidImpl;
import net.bither.R;
import net.bither.adapter.hot.HotFragmentPagerAdapter;
import net.bither.bitherj.AbstractApp;
import net.bither.bitherj.PrimerjSettings;
import net.bither.bitherj.core.Address;
import net.bither.bitherj.core.AddressManager;
import net.bither.bitherj.core.EnterpriseHDMAddress;
import net.bither.bitherj.core.HDMAddress;
import net.bither.bitherj.core.PeerManager;
import net.bither.bitherj.utils.Utils;
import net.bither.fragment.Refreshable;
import net.bither.fragment.Selectable;
import net.bither.fragment.Unselectable;
import net.bither.fragment.hot.HotAddressFragment;
import net.bither.fragment.hot.MarketFragment;
import net.bither.preference.AppSharedPreference;
import net.bither.runnable.AddErrorMsgRunnable;
import net.bither.runnable.DownloadAvatarRunnable;
import net.bither.runnable.UploadAvatarRunnable;
import net.bither.ui.base.BaseFragmentActivity;
import net.bither.ui.base.DropdownMessage;
import net.bither.ui.base.SyncProgressView;
import net.bither.ui.base.TabButton;
import net.bither.ui.base.dialog.DialogFirstRunWarning;
import net.bither.ui.base.dialog.DialogGenerateAddressFinalConfirm;
import net.bither.util.LogUtil;
import net.bither.util.StringUtil;
import net.bither.util.UIUtil;
import net.bither.util.WalletUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static net.bither.NotificationAndroidImpl.ACTION_UNSYNC_BLOCK_NUMBER_INFO;
import static net.bither.bitherj.core.PeerManager.ConnectedChangeBroadcast;

public class HotActivity extends BaseFragmentActivity {
    private TabButton tbtnMessage;
    private TabButton tbtnMain;
    private TabButton tbtnMe;
    private FrameLayout flAddAddress;
    private HotFragmentPagerAdapter mAdapter;
    private ViewPager mPager;
    private SyncProgressView pbSync;
    private LinearLayout llAlert;
    private TextView tvAlert;
    private ProgressBar pbAlert;

    private final TxAndBlockBroadcastReceiver txAndBlockBroadcastReceiver = new
            TxAndBlockBroadcastReceiver();
    private final ProgressBroadcastReceiver broadcastReceiver = new ProgressBroadcastReceiver();
    private final AddressIsLoadedReceiver addressIsLoadedReceiver = new AddressIsLoadedReceiver();
    private final AddressTxLoadingReceiver addressIsLoadingReceiver = new AddressTxLoadingReceiver();
    private final ConnectionStatusReceiver connectionStatusReceiver = new ConnectionStatusReceiver();

    protected void onCreate(Bundle savedInstanceState) {
        AbstractApp.notificationService.removeProgressState();
        AbstractApp.notificationService.removeAddressTxLoading();
        AbstractApp.notificationService.removeBroadcastPeerState();
        initAppState();
        super.onCreate(savedInstanceState);
        PrimerApplication.hotActivity = this;
        setContentView(R.layout.activity_hot);
        initView();
        registerReceiver();
        mPager.postDelayed(new Runnable() {
            @Override
            public void run() {
                initClick();
                mPager.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Fragment f = getActiveFragment();
                        if (f instanceof Selectable) {
                            ((Selectable) f).onSelected();
                        }
                    }
                }, 100);

                onNewIntent(getIntent());

            }
        }, 500);
        DialogFirstRunWarning.show(this);
        if (!NetworkUtil.isConnected()) {
            tvAlert.setText(R.string.tip_network_error);
            pbAlert.setVisibility(View.GONE);
            llAlert.setVisibility(View.VISIBLE);
        } else if (PeerManager.instance().getConnectedPeers().size() == 0) {
            if(!AddressManager.getInstance().addressIsSyncComplete()) {
                tvAlert.setText(R.string.tip_sync_address_tx_general);
            } else {
                tvAlert.setText(R.string.tip_no_peers_connected_scan);
            }
            pbAlert.setVisibility(View.VISIBLE);
            llAlert.setVisibility(View.VISIBLE);
        } else {
            Block block = BlockChain.getInstance().getLastBlock();
            final long timeMs = block.getBlockTime() * DateUtils.SECOND_IN_MILLIS;
            tvAlert.setText(getString(R.string.tip_block_height, block.getBlockNo()) +
                    DateUtils.getRelativeDateTimeString(this, timeMs, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0));
            llAlert.setVisibility(View.VISIBLE);
        }
    }

    private void registerReceiver() {
        registerReceiver(broadcastReceiver, new IntentFilter(NotificationAndroidImpl
                .ACTION_SYNC_BLOCK_AND_WALLET_STATE));
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(NotificationAndroidImpl.ACTION_SYNC_LAST_BLOCK_CHANGE);
        intentFilter.addAction(NotificationAndroidImpl.ACTION_ADDRESS_BALANCE);
        registerReceiver(txAndBlockBroadcastReceiver, intentFilter);
        registerReceiver(addressIsLoadedReceiver,
                new IntentFilter(NotificationAndroidImpl.ACTION_ADDRESS_LOAD_COMPLETE_STATE));
        registerReceiver(addressIsLoadingReceiver, new IntentFilter(NotificationAndroidImpl.ACTION_ADDRESS_TX_LOADING_STATE));
        IntentFilter connectionStatusIntentFilter = new IntentFilter();
        connectionStatusIntentFilter.addAction(NotificationAndroidImpl.ACTION_PEER_STATE);
        connectionStatusIntentFilter.addAction(ConnectedChangeBroadcast);
        registerReceiver(connectionStatusReceiver, connectionStatusIntentFilter);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(broadcastReceiver);
        unregisterReceiver(txAndBlockBroadcastReceiver);
        unregisterReceiver(addressIsLoadedReceiver);
        unregisterReceiver(addressIsLoadingReceiver);
        unregisterReceiver(connectionStatusReceiver);
        super.onDestroy();
        PrimerApplication.hotActivity = null;

    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PrimerApplication.startBlockchainService();
        //通知节点更新
        PeerManager.instance().notifyMaxConnectedPeerCountChange();
        refreshTotalBalance();
    }

    private void deleteNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context
                .NOTIFICATION_SERVICE);
        notificationManager.cancel(PrimerSetting.NOTIFICATION_ID_COINS_RECEIVED);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        deleteNotification();
        if (intent != null && intent.getExtras() != null && intent.getExtras().containsKey
                (PrimerSetting.INTENT_REF.NOTIFICATION_ADDRESS)) {
            final String address = intent.getExtras().getString(PrimerSetting.INTENT_REF
                    .NOTIFICATION_ADDRESS);
            mPager.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mPager.getCurrentItem() != 1) {
                        mPager.setCurrentItem(1, false);
                    }
                    Fragment fragment = getFragmentAtIndex(1);
                    if (fragment != null && fragment instanceof HotAddressFragment) {
                        ((HotAddressFragment) fragment).scrollToAddress(address);
                    }
                }
            }, 400);
        }
    }

    private void initView() {
        pbSync = (SyncProgressView) findViewById(R.id.pb_sync);
        flAddAddress = (FrameLayout) findViewById(R.id.fl_add_address);

        tbtnMain = (TabButton) findViewById(R.id.tbtn_main);
        tbtnMessage = (TabButton) findViewById(R.id.tbtn_message);
        tbtnMe = (TabButton) findViewById(R.id.tbtn_me);
        llAlert = (LinearLayout) findViewById(R.id.ll_alert);
        tvAlert = (TextView) findViewById(R.id.tv_alert);
        pbAlert = (ProgressBar) findViewById(R.id.pb_alert);

        configureTopBarSize();
        configureTabMainIcons();
        tbtnMain.setBigInteger(null, null, null, null, null, null);
        if (AbstractApp.addressIsReady) {
            refreshTotalBalance();
        }
        tbtnMessage.setIconResource(R.drawable.tab_market, R.drawable.tab_market_checked);
        tbtnMe.setIconResource(R.drawable.tab_option, R.drawable.tab_option_checked);
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.postDelayed(new Runnable() {
            @Override
            public void run() {
                mAdapter = new HotFragmentPagerAdapter(getSupportFragmentManager());
                mPager.setAdapter(mAdapter);
                mPager.setCurrentItem(1);
                mPager.setOffscreenPageLimit(2);
                mPager.setOnPageChangeListener(new PageChangeListener(new TabButton[]{tbtnMessage,
                        tbtnMain, tbtnMe}, mPager));
            }
        }, 100);
    }

    private void initClick() {
        flAddAddress.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                boolean isPrivateKeyLimit = AddressManager.isPrivateLimit();
                boolean isWatchOnlyLimit = AddressManager.isWatchOnlyLimit();
                if (isPrivateKeyLimit && isWatchOnlyLimit) {
                    DropdownMessage.showDropdownMessage(HotActivity.this,
                            R.string.private_key_count_limit);
                    DropdownMessage.showDropdownMessage(HotActivity.this,
                            R.string.watch_only_address_count_limit);
                    return;
                }
                //直接跳转到生成热钱包界面
//                Intent intent = new Intent(HotActivity.this, AddHotAddressActivity.class);
                Intent intent = new Intent(HotActivity.this, AddHotAddressPrivateKeyActivity.class);
                startActivityForResult(intent, PrimerSetting.INTENT_REF.SCAN_REQUEST_CODE);
                overridePendingTransition(R.anim.activity_in_drop, R.anim.activity_out_back);

            }
        });

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PrimerSetting.INTENT_REF.SCAN_REQUEST_CODE && resultCode == RESULT_OK) {
            ArrayList<String> addresses = (ArrayList<String>) data.getExtras().getSerializable
                    (PrimerSetting.INTENT_REF.ADDRESS_POSITION_PASS_VALUE_TAG);
            if (addresses != null && addresses.size() > 0) {
                Address a = WalletUtils.findPrivateKey(addresses.get(0));
                if (a != null && a.hasPrivKey() && !a.isFromXRandom()) {
                    new DialogGenerateAddressFinalConfirm(this, addresses.size(),
                            a.isFromXRandom()).show();
                }

                Fragment f = getFragmentAtIndex(1);
                if (f != null && f instanceof HotAddressFragment) {
                    mPager.setCurrentItem(1, true);
                    HotAddressFragment af = (HotAddressFragment) f;
                    af.showAddressesAdded(addresses);
                }
                if (f != null && f instanceof Refreshable) {
                    Refreshable r = (Refreshable) f;
                    r.doRefresh();
                }
            }
            return;
        }

        if (requestCode == SelectAddressToSendActivity.SEND_REQUEST_CODE && resultCode ==
                RESULT_OK) {
            DropdownMessage.showDropdownMessage(this, R.string.donate_thanks);
        }

    }

    private class PageChangeListener implements OnPageChangeListener {
        private List<TabButton> indicators;
        private ViewPager pager;

        public PageChangeListener(TabButton[] buttons, ViewPager viewPager) {
            this.indicators = new ArrayList<TabButton>();
            this.pager = viewPager;
            int size = buttons.length;
            for (int i = 0;
                 i < size;
                 i++) {
                TabButton button = buttons[i];
                indicators.add(button);
                if (pager.getCurrentItem() == i) {
                    button.setChecked(true);
                }
                button.setOnClickListener(new IndicatorClick(i));
            }

        }

        public void onPageScrollStateChanged(int state) {

        }

        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

        }

        private class IndicatorClick implements OnClickListener {

            private int position;

            public IndicatorClick(int position) {
                this.position = position;
            }

            public void onClick(View v) {
                if (pager.getCurrentItem() != position) {
                    pager.setCurrentItem(position, true);
                } else {
                    if (getActiveFragment() instanceof Refreshable) {
                        ((Refreshable) getActiveFragment()).doRefresh();
                    }
                    if (position == 1) {
                        tbtnMain.showDialog();
                    }
                }
            }
        }

        public void onPageSelected(int position) {

            if (position >= 0 && position < indicators.size()) {
                for (int i = 0;
                     i < indicators.size();
                     i++) {
                    indicators.get(i).setChecked(i == position);
                    if (i != position) {
                        Fragment f = getFragmentAtIndex(i);
                        if (f instanceof Unselectable) {
                            ((Unselectable) f).onUnselected();
                        }
                    }
                }
            }
            Fragment mFragment = getActiveFragment();
            if (mFragment instanceof Selectable) {
                ((Selectable) mFragment).onSelected();
            }
        }
    }

    public void scrollToFragmentAt(int index) {
        if (mPager.getCurrentItem() != index) {
            mPager.setCurrentItem(index, true);
        }
    }

    private void configureTopBarSize() {
        int sideBarSize = UIUtil.getScreenWidth() / 3 - UIUtil.getScreenWidth() / 18;
        tbtnMessage.getLayoutParams().width = sideBarSize;
        tbtnMe.getLayoutParams().width = sideBarSize;
    }

    public Fragment getFragmentAtIndex(int i) {
        String str = StringUtil.makeFragmentName(this.mPager.getId(), i);
        return getSupportFragmentManager().findFragmentByTag(str);
    }

    public Fragment getActiveFragment() {
        Fragment localFragment = null;
        if (this.mPager == null) {
            return localFragment;
        }
        localFragment = getFragmentAtIndex(mPager.getCurrentItem());
        return localFragment;
    }

    private void initAppState() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                AppSharedPreference.getInstance().touchLastUsed();
                AddErrorMsgRunnable addErrorMsgRunnable = new AddErrorMsgRunnable();
                addErrorMsgRunnable.run();
                UploadAvatarRunnable uploadAvatarRunnable = new UploadAvatarRunnable();
                uploadAvatarRunnable.run();
                DownloadAvatarRunnable downloadAvatarRunnable = new DownloadAvatarRunnable();
                downloadAvatarRunnable.run();
            }
        }).start();
    }

    public void refreshTotalBalance() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                long totalPrivate = 0;
                long totalWatchOnly = 0;
                long totalHdm = 0;
                long totalEnterpriseHdm = 0;
                for (Address address : AddressManager.getInstance().getPrivKeyAddresses()) {
                    totalPrivate += address.getBalance();
                }
                for (Address address : AddressManager.getInstance().getWatchOnlyAddresses()) {
                    totalWatchOnly += address.getBalance();
                }
                if (AddressManager.getInstance().hasHDMKeychain()) {
                    for (HDMAddress address : AddressManager.getInstance().getHdmKeychain()
                            .getAddresses()) {
                        totalHdm += address.getBalance();
                    }
                }
                if (AddressManager.getInstance().hasEnterpriseHDMKeychain()) {
                    for (EnterpriseHDMAddress address : AddressManager.getInstance()
                            .getEnterpriseHDMKeychain().getAddresses()) {
                        totalEnterpriseHdm += address.getBalance();
                    }
                }
                final long btcPrivate = totalPrivate;
                final long btcWatchOnly = totalWatchOnly;
                final long btcHdm = totalHdm;
                final long btcEnterpriseHdm = totalEnterpriseHdm;
                final long btcHD = AddressManager.getInstance().hasHDAccountHot() ? AddressManager
                        .getInstance().getHDAccountHot().getBalance() : 0;
                final long btcHdMonitored = AddressManager.getInstance().hasHDAccountMonitored()
                        ? AddressManager.getInstance().getHDAccountMonitored().getBalance() : 0;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        configureTabMainIcons();
                        tbtnMain.setBigInteger(BigInteger.valueOf(btcPrivate), BigInteger.valueOf
                                        (btcWatchOnly), BigInteger.valueOf(btcHdm), BigInteger.valueOf
                                        (btcHD), BigInteger.valueOf(btcHdMonitored),
                                BigInteger.valueOf(btcEnterpriseHdm));
                    }
                });
            }
        }).start();
    }

    private void configureTabMainIcons() {
        switch (AppSharedPreference.getInstance().getBitcoinUnit()) {
            case XPM:
            default:
                tbtnMain.setIconResource(R.drawable.tab_main, R.drawable.tab_main_checked);
        }
    }

    public void notifPriceAlert(PrimerjSettings.MarketType marketType) {
        if (mPager.getCurrentItem() != 0) {
            mPager.setCurrentItem(0);
        }
        Fragment fragment = getActiveFragment();
        if (fragment instanceof MarketFragment) {
            MarketFragment marketFragment = (MarketFragment) fragment;
            marketFragment.notifPriceAlert(marketType);
        }
    }

    //接收请求进度广播
    private final class ProgressBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (intent != null) {
                if (intent.hasExtra(NotificationAndroidImpl.ACTION_PROGRESS_INFO)) {
                    double progress = intent.getDoubleExtra(NotificationAndroidImpl.ACTION_PROGRESS_INFO, 0);
                    LogUtil.d("progress", "BlockchainBroadcastReceiver" + progress);
                    pbSync.setProgress(progress);
                }
                if (intent.hasExtra(ACTION_UNSYNC_BLOCK_NUMBER_INFO)) {
                    long unsyncBlockNumber = intent.getLongExtra(ACTION_UNSYNC_BLOCK_NUMBER_INFO, 0);
                    if (unsyncBlockNumber > 0) {
                        tvAlert.setText(getString(R.string.tip_sync_block_height, unsyncBlockNumber));
                        pbAlert.setVisibility(View.VISIBLE);
                    } else {
                        Block block = BlockChain.getInstance().getLastBlock();
                        final long timeMs = block.getBlockTime() * DateUtils.SECOND_IN_MILLIS;
                        tvAlert.setText(getString(R.string.tip_block_height, block.getBlockNo()) +
                                DateUtils.getRelativeDateTimeString(context, timeMs, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0));
                        pbAlert.setVisibility(View.GONE);
                    }
                }
            }
        }
    }

    public void showProgressBar() {
        pbSync.setProgress(0.6);
    }

    //刷新TX
    private final class TxAndBlockBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null ||
                    (!Utils.compareString(NotificationAndroidImpl.ACTION_ADDRESS_BALANCE, intent.getAction())
                            && !Utils.compareString(NotificationAndroidImpl.ACTION_SYNC_LAST_BLOCK_CHANGE, intent.getAction()))) {
                return;
            }
            if (Utils.compareString(NotificationAndroidImpl.ACTION_ADDRESS_BALANCE, intent.getAction())) {
                refreshTotalBalance();
            }
            Fragment fragment = getFragmentAtIndex(1);
            if (fragment != null && fragment instanceof HotAddressFragment) {
                ((HotAddressFragment) fragment).refresh();
                pbSync.setProgress(-1);
            }
        }
    }

    private final class AddressIsLoadedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !Utils.compareString(intent.getAction(), NotificationAndroidImpl.ACTION_ADDRESS_LOAD_COMPLETE_STATE)) {
                return;
            }
            refreshTotalBalance();
            Fragment fragment = getFragmentAtIndex(1);
            if (fragment != null && fragment instanceof HotAddressFragment) {
                ((HotAddressFragment) fragment).refresh();
            }
        }
    }

    private ShowImportSuccessListener showImportSuccessListener;

    public void setShowImportSuccessListener(ShowImportSuccessListener showImportSuccessListener) {
        this.showImportSuccessListener = showImportSuccessListener;
    }

    public interface ShowImportSuccessListener {

        void showImportSuccess();
    }

    public void showImportSuccess() {
        if (showImportSuccessListener != null)
            showImportSuccessListener.showImportSuccess();
    }

    private final class AddressTxLoadingReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !Utils.compareString(intent.getAction(), NotificationAndroidImpl.ACTION_ADDRESS_TX_LOADING_STATE)) {
                return;
            }
            if (!intent.hasExtra(NotificationAndroidImpl.ACTION_ADDRESS_TX_LOADING_INFO)) {
                return;
            }
            String address = intent.getStringExtra(NotificationAndroidImpl.ACTION_ADDRESS_TX_LOADING_INFO);
            if (Utils.isEmpty(address)) {
                Block block = BlockChain.getInstance().getLastBlock();
                final long timeMs = block.getBlockTime() * DateUtils.SECOND_IN_MILLIS;
                tvAlert.setText(getString(R.string.tip_block_height, block.getBlockNo()) +
                        DateUtils.getRelativeDateTimeString(context, timeMs, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0));
                pbAlert.setVisibility(View.GONE);
                return;
            }
            tvAlert.setText(getString(R.string.tip_sync_address_tx, address));
            pbAlert.setVisibility(View.VISIBLE);
        }
    }

    private final class ConnectionStatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || (!Utils.compareString(intent.getAction(), NotificationAndroidImpl.ACTION_PEER_STATE) && !Utils.compareString(intent.getAction(), ConnectedChangeBroadcast))) {
                return;
            }
            if (!NetworkUtil.isConnected()) {
                tvAlert.setText(R.string.tip_network_error);
                pbAlert.setVisibility(View.GONE);
                return;
            } else if (PeerManager.instance().getConnectedPeers().size() == 0) {
                if(AddressManager.getInstance().addressIsSyncComplete()) {
                    tvAlert.setText(R.string.tip_no_peers_connected_scan);
                    pbAlert.setVisibility(View.VISIBLE);
                }
                return;
            }
            String tvstring = tvAlert.getText().toString();
            if (tvstring == getResources().getString(R.string.tip_network_error) || tvstring == getResources().getString(R.string.tip_no_peers_connected_scan)) {
                Block block = BlockChain.getInstance().getLastBlock();
                final long timeMs = block.getBlockTime() * DateUtils.SECOND_IN_MILLIS;
                tvAlert.setText(getString(R.string.tip_block_height, block.getBlockNo()) +
                        DateUtils.getRelativeDateTimeString(context, timeMs, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0));
                pbAlert.setVisibility(View.GONE);
            }
        }
    }

//    private void addNewPrivateKey() {
//        final AppSharedPreference preference = AppSharedPreference.getInstance();
//        if (!preference.hasPrivateKey()) {
//            dp = new DialogProgress(HotActivity.this, R.string.please_wait);
//            dp.setCancelable(false);
//            DialogPassword dialogPassword = new DialogPassword(HotActivity.this,
//                    new DialogPasswordListener() {
//
//                        @Override
//                        public void onPasswordEntered(final SecureCharSequence password) {
//                            ThreadNeedService thread = new ThreadNeedService(dp, HotActivity.this) {
//
//                                @Override
//                                public void runWithService(BlockchainService service) {
//
//                                    ECKey ecKey = PrivateKeyUtil.encrypt(new ECKey(), password);
//                                    Address address = new Address(ecKey);
//                                    List<Address> addressList = new ArrayList<Address>();
//                                    addressList.add(address);
//                                    if (!AddressManager.getInstance().getAllAddresses().contains(address)) {
//
//                                        password.wipe();
//                                        KeyUtil.addAddressListByDesc(service, addressList);
//                                        preference.setHasPrivateKey(true);
//                                    }
//                                    password.wipe();
//                                    HotActivity.this.runOnUiThread(new Runnable() {
//                                        @Override
//                                        public void run() {
//
//                                            if (dp.isShowing()) {
//                                                dp.dismiss();
//                                            }
//                                            Fragment fragment = getFragmentAtIndex(1);
//                                            if (fragment instanceof Refreshable) {
//                                                ((Refreshable) fragment).doRefresh();
//                                            }
//
//                                            new DialogConfirmTask(HotActivity.this,
//                                                    getString(R.string
//                                                            .first_add_private_key_check_suggest),
//                                                    new Runnable() {
//                                                        @Override
//                                                        public void run() {
//                                                            ThreadUtil.runOnMainThread(new Runnable() {
//                                                                @Override
//                                                                public void run() {
//                                                                    Intent intent = new Intent(HotActivity.this,
//                                                                            CheckPrivateKeyActivity.class);
//                                                                    intent.putExtra(PrimerSetting.INTENT_REF
//                                                                                    .ADD_PRIVATE_KEY_SUGGEST_CHECK_TAG, true
//                                                                    );
//                                                                    startActivity(intent);
//                                                                }
//                                                            });
//                                                        }
//                                                    }
//                                            ).show();
//                                        }
//                                    });
//                                }
//                            };
//                            thread.start();
//                        }
//                    }
//            );
//            dialogPassword.setCancelable(false);
//            dialogPassword.show();
//        }
//    }
}
