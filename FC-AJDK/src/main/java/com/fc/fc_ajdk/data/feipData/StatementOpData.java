package com.fc.fc_ajdk.data.feipData;

public class StatementOpData {
	
	private String title;
	private String content;
	private String confirm;

	public static final String CONFIRM = "This is a formal and irrevocable statement.";

	public static StatementOpData makeStatement(String title, String content) {
		StatementOpData data = new StatementOpData();
		data.setTitle(title);
		data.setContent(content);
		data.setConfirm(CONFIRM);
		return data;
	}
	
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
	public String getConfirm() {
		return confirm;
	}
	public void setConfirm(String confirm) {
		this.confirm = confirm;
	}
}
