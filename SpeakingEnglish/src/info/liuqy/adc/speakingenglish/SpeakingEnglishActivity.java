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
import android.widget.TextView;
import android.widget.Toast;

public class SpeakingEnglishActivity extends TabActivity {

	private static final String TAB1 = "TAB1";
	private static final String TAB2 = "TAB2";
	private static final String TAB3 = "TAB3";
	private static final String CN2EN = "中译英";
	private static final String EN2CN = "en2cn";
	private static final String HAN2PIN = "汉字->拼音";
	private static final int TAB_CN2EN = 1;
	private static final int TAB_EN2CN = 0;

	SharedPreferences mSharedPreferences;
	SharedPreferences.Editor mEditor;
	List<String> cns = new ArrayList<String>();
	String mTabId = null;
	ArrayAdapter<String> adapter;
	ListView mListView;

	// all expressions cn => en
	Map<String, String> exprs = null;

	private class DFA {
		private static final int INIT_STATE = 0, EXPR_TAG = 1, CN_TAG = 2,
				EN_TAG = 3, CN_TEXT = 4, EN_TEXT = 5, PRE_FINAL = 6,
				FINAL_STATE = 7;
		int currentState = 0;
		Map<Integer, Map<String, Integer>> T = new HashMap<Integer, Map<String, Integer>>();

		public DFA() {
			Map<String, Integer> m = new HashMap<String, Integer>();
			m.put("expression", EXPR_TAG);
			T.put(INIT_STATE, m);
			m = new HashMap<String, Integer>();
			m.put("cn", CN_TAG);
			m.put("en", EN_TAG);
			T.put(EXPR_TAG, m);
			m = new HashMap<String, Integer>();
			m.put("text", CN_TEXT);
			T.put(CN_TAG, m);
			m = new HashMap<String, Integer>();
			m.put("text", EN_TEXT);
			T.put(EN_TAG, m);
			m = new HashMap<String, Integer>();
			m.put("en", PRE_FINAL);
			T.put(CN_TEXT, m);
			m = new HashMap<String, Integer>();
			m.put("cn", PRE_FINAL);
			T.put(EN_TEXT, m);
			m = new HashMap<String, Integer>();
			m.put("text", FINAL_STATE);
			T.put(PRE_FINAL, m);
		}

		public void reset() {
			currentState = 0;
		}

		public int nextState(String symbol) {
			if (currentState != FINAL_STATE
					&& T.get(currentState).containsKey(symbol))
				currentState = T.get(currentState).get(symbol);
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
		tabHost.addTab(tab1.setIndicator(EN2CN).setContent(R.id.mlist));
		tabHost.addTab(tab2.setIndicator(CN2EN).setContent(R.id.mlist));
		tabHost.addTab(tab3.setIndicator(HAN2PIN).setContent(R.id.mlist));

		// 设定tab样式
		for (int i = 0; i < tabHost.getTabWidget().getTabCount(); ++i) {
			TextView tv = (TextView) tabHost.getTabWidget().getChildAt(i)
					.findViewById(android.R.id.title);
			tv.setTextSize(20);
			tabHost.getTabWidget().getChildAt(i).getLayoutParams().height = 30;
		}

		mListView = (ListView) findViewById(R.id.mlist);
		// 监听list点击事件，翻译
		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				TextView tv = (TextView) arg1;
				String text = tv.getText().toString();
				if (exprs.containsKey(text)) // Chinese displayed now
					tv.setText(exprs.get(text));
				else
					// English displayed now, refresh the display
					adapter.notifyDataSetChanged();

			}
		});

		String tab = mSharedPreferences.getString("tab", null);
		if (tab == null) {
			// 识别系统语言环境
			if ("zh".equals(Locale.getDefault().getLanguage())) {
				tabHost.setCurrentTab(TAB_CN2EN);
				changeAdapter(R.xml.cn2en, "cn");
			} else if ("en".equals(Locale.getDefault().getLanguage())) {
				tabHost.setCurrentTab(TAB_EN2CN);
				changeAdapter(R.xml.cn2en, "en");
			}
		} else {
			tabHost.setCurrentTabByTag(tab);
			if ("TAB1".equals(tab)) {
				changeAdapter(R.xml.cn2en, "en");
			} else if ("TAB2".equals(tab)) {
				changeAdapter(R.xml.cn2en, "cn");
			} else {
				changeAdapter(R.xml.hanzi2pinyin, "en");
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
					changeAdapter(R.xml.hanzi2pinyin, "en");
				}
				mTabId = tabId;
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
		String cn = null, en = null;
		while (xpp.getEventType() != XmlPullParser.END_DOCUMENT) {
			if (xpp.getEventType() == XmlPullParser.START_TAG) {
				dfa.nextState(xpp.getName());
			} else if (xpp.getEventType() == XmlPullParser.TEXT) {
				int state = dfa.nextState("text");
				if (state == DFA.CN_TEXT)
					cn = xpp.getText();
				else if (state == DFA.EN_TEXT)
					en = xpp.getText();
				else if (state == DFA.FINAL_STATE) {
					if (cn == null)
						cn = xpp.getText();
					else if (en == null)
						en = xpp.getText();

					if ("cn".equals(language)) {
						exprs.put(cn, en);
					} else {
						exprs.put(en, cn);
					}
					dfa.reset();
					cn = en = null;
				}
			}
			xpp.next();
		}
		return exprs;
	}

}