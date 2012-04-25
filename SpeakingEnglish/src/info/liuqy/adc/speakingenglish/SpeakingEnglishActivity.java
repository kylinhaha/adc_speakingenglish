package info.liuqy.adc.speakingenglish;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.TabActivity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;
import android.widget.TextView;
import android.widget.Toast;

public class SpeakingEnglishActivity extends TabActivity implements TabContentFactory{

	private static final String TAB1 = "TAB1";
	private static final String TAB2 = "TAB2";
	private static final String TAB3 = "TAB3";
	private static final String CN2EN = "中译英";
	private static final String EN2CN = "en2cn";
	private static final String HAN2PIN = "汉字->拼音";
	private static final int IDX_CN2EN = 1;
	private static final int IDX_EN2CN = 0;

	SharedPreferences mSharedPreferences;
	SharedPreferences.Editor mEditor;
	List<String> cns = new ArrayList<String>();
	String mTabId = null;
	ArrayAdapter<String> adapter;
	ListView mListView;
	Map<String, String> stackMap = new HashMap<String, String>();

	// all expressions cn => en
	Map<String, String> exprs = null;

	private class DFA {
		private static final int CN_TEXT = 1, EN_TEXT = 2, PIN_TEXT=3;
		int currentState = 0;

		Map<String, Integer> map = new HashMap<String, Integer>();
		public DFA() {
			map.put("cn", CN_TEXT);
			map.put("en", EN_TEXT);
			map.put("pin", PIN_TEXT);
		}

		public void reset() {
			currentState = 1;
		}

		public int nextState(String symbol) {
			if (map.containsKey(symbol))
				currentState = map.get(symbol);
			return currentState;
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mSharedPreferences = getSharedPreferences("tab", MODE_PRIVATE);
		mEditor = mSharedPreferences.edit();
		
		// tab对象
		TabHost tabHost = getTabHost();
		// 加载tab控件
		LayoutInflater.from(this).inflate(R.layout.main,
				tabHost.getTabContentView(), true);

		TabHost.TabSpec tab1 = tabHost.newTabSpec(TAB1);
		TabHost.TabSpec tab2 = tabHost.newTabSpec(TAB2);
		TabHost.TabSpec tab3 = tabHost.newTabSpec(TAB3);
		tabHost.addTab(tab1.setIndicator(EN2CN).setContent(this));
		tabHost.addTab(tab2.setIndicator(CN2EN).setContent(this));
		tabHost.addTab(tab3.setIndicator(HAN2PIN).setContent(this));

		// 设定tab样式
		for (int i = 0; i < tabHost.getTabWidget().getTabCount(); ++i) {
			TextView tv = (TextView) tabHost.getTabWidget().getChildAt(i)
					.findViewById(android.R.id.title);
			tv.setTextSize(20);
			tabHost.getTabWidget().getChildAt(i).getLayoutParams().height = 30;
		}

		// 监听list点击事件，翻译
		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				TextView tv = (TextView) arg1;
				String text = tv.getText().toString();
				if (exprs.containsKey(text)){ // Chinese displayed now
					tv.setText(exprs.get(text));
					stackMap.put(exprs.get(text), text);
				} else if (exprs.containsValue(text)) {
					tv.setText(stackMap.get(text));
				} else
					// English displayed now, refresh the display
					adapter.notifyDataSetChanged();
			}
		});

		String tab = mSharedPreferences.getString("tab", null);
		if (tab == null) {
			// 识别系统语言环境
			if ("zh".equals(Locale.getDefault().getLanguage())) {
				tabHost.setCurrentTab(IDX_CN2EN);
				changeAdapter(R.xml.cn2en, "cn");
			} else if ("en".equals(Locale.getDefault().getLanguage())) {
				tabHost.setCurrentTab(IDX_EN2CN);
				changeAdapter(R.xml.cn2en, "en");
			}
		} else {
			tabHost.setCurrentTabByTag(tab);
			if ("TAB1".equals(tab)) {
				changeAdapter(R.xml.cn2en, "en");
			} else if ("TAB2".equals(tab)) {
				changeAdapter(R.xml.cn2en, "cn");
			} else {
				changeAdapter(R.xml.cn2en, "pin");
			}
		}

		// 监听tab
		tabHost.setOnTabChangedListener(new OnTabChangeListener() {
			@Override
			public void onTabChanged(String tabId) {
				if (TAB1.equals(tabId)) {
					changeAdapter(R.xml.cn2en, "en");
				} else if (TAB2.equals(tabId)) {
					changeAdapter(R.xml.cn2en, "cn");
				} else {
					changeAdapter(R.xml.cn2en, "pin");
				}
				mTabId = tabId;
				stackMap.clear();
			}
		});

	}

	@Override
	protected void onStop() {
		mEditor.putString("tab", mTabId);
		mEditor.commit();
		super.onStop();
	}
	
	// 设定数据集
	private void changeAdapter(int xmlLanguage, String language) {
		try {
			exprs = loadExpressionsFromXml(xmlLanguage, language);
		} catch (IOException e) {
			Toast.makeText(this, R.string.error_xml_file, Toast.LENGTH_SHORT);
		} catch (XmlPullParserException e) {
			Toast.makeText(this, R.string.error_parsing_xml, Toast.LENGTH_SHORT);
		}

		cns.clear();
		for (String key : exprs.keySet()) {
			cns.add(key);
		}
		adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, cns);

		mListView.setAdapter(adapter);
	}

	// 解析xml
	private Map<String, String> loadExpressionsFromXml(int resourceId, String language)
			throws XmlPullParserException, IOException {

		Map<String, String> exprs = new HashMap<String, String>();
		XmlPullParser xpp = getResources().getXml(resourceId);
		DFA dfa = new DFA();
		String cn = null, en = null, pin = null;
		int eventType = xpp.getEventType();
		int state = 0;
		while (eventType != XmlPullParser.END_DOCUMENT) {

			if (eventType == XmlPullParser.START_TAG) {
				state = dfa.nextState(xpp.getName());
			} else if (eventType == XmlPullParser.TEXT) {
				if (state == DFA.CN_TEXT)
					cn = xpp.getText();
				else if (state == DFA.EN_TEXT)
					en = xpp.getText();
				else if (state == DFA.PIN_TEXT) {
					pin = xpp.getText();
				}
				dfa.reset();
				if (cn != null && en != null && pin != null) {
					if ("cn".equals(language)) {
						exprs.put(cn, en);
					} else if ("en".equals(language)) {
						exprs.put(en, cn);
					} else {
						exprs.put(pin, cn);
					}
					cn = en = pin = null;
				}
			}
			eventType = xpp.next();
		}
		return exprs;
	}

	@Override
	public View createTabContent(String tag) {
		mListView = (ListView) findViewById(R.id.mlist);
		return mListView;
	}

}