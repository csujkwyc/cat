package com.dianping.cat.report.page.jsError;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;

import org.unidal.lookup.annotation.Inject;
import org.unidal.web.mvc.PageHandler;
import org.unidal.web.mvc.annotation.InboundActionMeta;
import org.unidal.web.mvc.annotation.OutboundActionMeta;
import org.unidal.web.mvc.annotation.PayloadMeta;

import com.dianping.cat.Cat;
import com.dianping.cat.message.Message;
import com.dianping.cat.message.Transaction;
import com.dianping.cat.message.spi.internal.DefaultMessageTree;
import com.dianping.cat.report.ReportPage;

public class Handler implements PageHandler<Context> {
	@Inject
	private JspViewer m_jspViewer;

	private static final String ACCESS = "DirectAccess";

	@Override
	@PayloadMeta(Payload.class)
	@InboundActionMeta(name = "jsError")
	public void handleInbound(Context ctx) throws ServletException, IOException {
		// display only, no action here
	}

	private String parseHost() {
		DefaultMessageTree tree = (DefaultMessageTree) Cat.getManager().getThreadLocalMessageTree();
		Message message = tree.getMessage();

		if (message.getType().equals("URL") && message instanceof Transaction) {
			Transaction t = (Transaction) message;
			List<Message> messages = t.getChildren();

			for (Message temp : messages) {
				String type = temp.getType();
				if (type.equals("URL.Server") || type.equals("ClientInfo")) {
					String data = temp.getData().toString();
					String url = parseValue("Referer", data);

					if (url != null) {
						try {
							URL u = new URL(url);
							return u.getHost();
						} catch (MalformedURLException e) {
							break;
						}
					}
				}
			}
		}
		return ACCESS;
	}

	protected String parseValue(final String key, final String data) {
		int len = data == null ? 0 : data.length();
		int keyLen = key.length();
		StringBuilder name = new StringBuilder();
		StringBuilder value = new StringBuilder();
		boolean inName = true;

		for (int i = 0; i < len; i++) {
			char ch = data.charAt(i);

			switch (ch) {
			case '&':
				if (name.length() == keyLen && name.toString().equals(key)) {
					return value.toString();
				}
				inName = true;
				name.setLength(0);
				value.setLength(0);
				break;
			case '=':
				if (inName) {
					inName = false;
				} else {
					value.append(ch);
				}
				break;
			default:
				if (inName) {
					name.append(ch);
				} else {
					value.append(ch);
				}
				break;
			}
		}

		if (name.length() == keyLen && name.toString().equals(key)) {
			return value.toString();
		}

		return null;
	}

	@Override
	@OutboundActionMeta(name = "jsError")
	public void handleOutbound(Context ctx) throws ServletException, IOException {
		Model model = new Model(ctx);
		Payload payload = ctx.getPayload();

		long timestamp = payload.getTimestamp();
		String error = payload.getError();
		String file = payload.getFile();
		String host = parseHost();

		if (file == null || file.length() == 0) {
			file = "unknown";
		}
		int index = file.indexOf('?');

		if (index > -1) {
			file = file.substring(0, index);
		}
		Cat.logEvent("Error", file, "Error", error);
		Cat.logEvent("Error.Date", file, Message.SUCCESS,
		      new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(timestamp)));

		DefaultMessageTree tree = (DefaultMessageTree) Cat.getManager().getThreadLocalMessageTree();
		tree.setDomain("FrontEnd");
		tree.setHostName(host);
		tree.setIpAddress(host);

		model.setStatus("SUCCESS");
		model.setAction(Action.VIEW);
		model.setPage(ReportPage.JSERROR);
		m_jspViewer.view(ctx, model);
	}
}
