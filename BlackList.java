package com.chuanke.pay.proofread;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainService {
	protected static final Logger logger = LoggerFactory.getLogger(MainService.class);

	static String DRIVER = "";
	static String HOST = "";
	static String USER_NAME = "";
	static String PASSWORD = "";
	static String TIMES = "5";
	
	static String SMTPHost = "";
	static String SMTPUsername = "";
	static String SMTPPassword = "";
	static String SEND_TO = "";

	static Connection conn;
	static PreparedStatement pst;

	static {
		InputStream is = MainService.class.getClassLoader().getResourceAsStream("config.properties");
		Properties prop = new Properties();
		try {
			prop.load(is);
		} catch (Exception ex) {
			logger.error("读取config.properties配置文件出错", ex);
		}
		DRIVER = prop.getProperty("jdbc.driver");
		HOST = prop.getProperty("jdbc.url");
		USER_NAME = prop.getProperty("jdbc.username");
		PASSWORD = prop.getProperty("jdbc.password");
		TIMES = prop.getProperty("order.times");
		SMTPHost = prop.getProperty("mail.SMTPHost");
		SMTPUsername = prop.getProperty("mail.SMTPUsername");
		SMTPPassword = prop.getProperty("mail.SMTPPassword");
		SEND_TO = prop.getProperty("mail.sendTo");
	}
	
	public static void sendMail(List<String> memberList) throws EmailException {
		SimpleEmail email = new SimpleEmail();
		email.setHostName(SMTPHost);
		email.setAuthentication(SMTPUsername, SMTPPassword);
		if(!"".equals(SEND_TO)) {
			String[] sendTos = SEND_TO.split(";");
			if(sendTos != null && sendTos.length > 0) {
				for(String sendTo : sendTos) {
					email.addTo(sendTo);
				}
			}
		}
		email.setFrom(SMTPUsername, "民生商城公共邮箱");
		email.setSubject("信用卡商城以下用户已被加入黑名单");
		email.setCharset("UTF-8");
		email.setMsg("以下用户" + memberList.toString() + "已被自动加入黑名");
		email.send();
	}

	public static final Connection getConnection() {
		Connection con = null;
		try {
			Class.forName(DRIVER);
			con = DriverManager.getConnection(HOST, USER_NAME, PASSWORD);
		} catch (Exception e) {
			logger.error("数据库连接失", e);
		}
		return con;
	}

	/**
	 * 查询符合条件的订单列"
	 * 
	 * @return 返回订单列表
	 */
	public static List<String[]> queryOrders() {
		conn = getConnection();
		try {
			String sql = "SELECT o.member,o.consignee,o.address,o.phone FROM xx_order o, xx_order_item oi WHERE o.create_date > date_sub(CURDATE(), INTERVAL 1 DAY) AND  o.create_date < CURDATE() AND o.id=oi.orders AND oi.product IN (SELECT pl.product_id FROM xx_product_limit pl WHERE pl.start_date < NOW() AND pl.end_date > NOW())";
			logger.info("查询订单sql" + sql);
			pst = conn.prepareStatement(sql);
			ResultSet rs = pst.executeQuery(sql);
			List<String[]> orderList = new ArrayList<String[]>();
			while (rs.next()) {
				String[] order = new String[4];
				order[0] = rs.getString("member");
				order[1] = rs.getString("consignee");
				order[2] = rs.getString("address");
				order[3] = rs.getString("phone");
				orderList.add(order);
			}
			return orderList;
		} catch (SQLException e) {
			logger.error("查询数据库失", e);
			try {
				pst.close();
				conn.close();
			} catch (SQLException e1) {
			}
		}
		return null;
	}

	/**
	 * 处理订单列表
	 * 
	 * @param orderList
	 * @return 返回"��插入黑名单表的用户ID集合
	 */
	public static List<String> handleMemberBlacklist(List<String[]> orderList) {
		List<String[]> orderInnerList = new ArrayList<String[]>(); // 拷贝列表
		orderInnerList.addAll(orderList);
		List<String> memberBlacklist = new ArrayList<String>();
		for (int i=0; i<orderList.size(); i++) {
			String[] order = orderList.get(i);
			int totalNum = 0;
			List<String[]> orderTempList = new ArrayList<String[]>();
			// 内层循环
			for (int j=i+1; j<orderInnerList.size(); j++) {
				String[] orderInner = orderInnerList.get(j);
				// 判断只要有member、address、phone中一个字段相同，这条订单就符合刷单规则，totalNum"
				// 不对收货人进行判断，可能会存在非真实的名字如：张先生、大宝等
				if (orderInner[0] == order[0] || orderInner[2] == order[2] || orderInner[3] == order[3]) {
					totalNum++;
					orderTempList.add(orderInner);
				}
			}
			if (totalNum >= Integer.valueOf(TIMES)) {
				memberBlacklist.add(order[0]);
				if (orderTempList != null && orderTempList.size() > 0) {
					for (String[] orderTemp : orderTempList) {
						if (!memberBlacklist.contains(orderTemp[0])) {
							memberBlacklist.add(orderTemp[0]);
						}
					}
				}
			}
			if (orderTempList != null && orderTempList.size() > 0) {
				// 内层循环完毕，剔除已参与判断的order记录
				orderInnerList.removeAll(orderTempList);
				orderTempList.clear();
			}
		}
		logger.info("加入黑名单的用户列表" + memberBlacklist.toString());
		return memberBlacklist;
	}

	/**
	 * 将过滤后的用户id插入黑名单表"
	 * 
	 * @param memberList
	 */
	public static void insertMemberBlacklist(List<String> memberList) {
		try {
			conn.setAutoCommit(false);
			String sql = "SELECT userId FROM xx_member_blacklist";
			pst = conn.prepareStatement(sql);
			ResultSet rs = pst.executeQuery(sql);
			List<String> userIdList = new ArrayList<String>();
			while (rs.next()) {
				String userId = rs.getString("userId");
				userIdList.add(userId);
			}
			if (memberList != null && memberList.size() > 0
					&& userIdList != null && userIdList.size() > 0) {
				memberList.removeAll(userIdList);
				logger.info("用户ID为：" + userIdList.toString()
						+ "的用户已经在黑名单表中存在，本次不做数据插入");
			}
			String insertSql = "insert into xx_member_blacklist(userId,create_date,modify_date) values(?,?,?)";
			pst = conn.prepareStatement(insertSql);
			for (String memberId : memberList) {
				pst.setString(1, memberId);
				pst.setTimestamp(2, new Timestamp(new Date().getTime()));
				pst.setTimestamp(3, new Timestamp(new Date().getTime()));
				pst.addBatch();
			}
			pst.executeBatch();
			conn.commit();
			logger.info("已成功将用户ID为：" + memberList.toString() + "的用户插入到黑名单表");
		} catch (SQLException e) {
			logger.error("插入黑名单用户出现异", e);
		} finally {
			try {
				pst.close();
				conn.close();
			} catch (SQLException e) {
			}
		}
	}

	public static void main(String[] args) {
		// 方法入口
		List<String[]> orderList = queryOrders();
		List<String> memberList = handleMemberBlacklist(orderList);
		insertMemberBlacklist(memberList);
		if(memberList != null && memberList.size() > 0) {
			try {
				sendMail(memberList);
			} catch (EmailException e) {
				e.printStackTrace();
			}
		}
	}
}
