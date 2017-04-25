package com.suisrc.weixin.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import com.suisrc.weixin.core.api.AccessTokenRest;
import com.suisrc.weixin.core.bean.WxAccessToken;
import com.suisrc.weixin.core.bean.WxAccessToken.Status;

/**
 * 程序入口配置抽象
 * @author Y13
 */
public abstract class AbstractWeixinActivator /* implements ApiActivator, WxConfig */ {

	/**
	 * 注入远程获取AccessTokenRest接口
	 */
	private @Inject AccessTokenRest accessTokenRest;

	/**
	 * 微信公众号的appid
	 */
	protected String appId;
	
	/**
	 * 微信公众号的app corpSecret
	 */
	protected String appSecret;
	
	/**
	 * 微信公众号的token
	 */
	protected String token;
	
	/**
	 * 消息加解密密钥
	 */
	protected String encodingAesKey;
	
	/**
	 * 获取基础路径地址
	 */
	protected String baseUrl;
	
	/**
	 * 提供器工厂
	 */
	protected ResteasyProviderFactory providerFactory = null;
	
	/**
	 * 异步多线程访问缓冲
	 */
	protected ExecutorService executor = null;
	
	/**
	 * 访问的客户端
	 */
	protected Client client;
	
	/**
	 * access token
	 * 强原子操作
	 */
//	protected volatile WxAccessToken accessToken;
	protected AtomicReference<WxAccessToken> accessToken = new AtomicReference<>();
	/**
	 * access token 正在同步中，该字段是给异步同时时候使用的，避免多次异步同步更新
	 */
	protected AtomicBoolean synchronize = new AtomicBoolean(false);
	
	/**
	 * 构造后被系统调用
	 * 进行内容初始化
	 */
	public void initialized() {
		WxAccessToken token = readAccessToken(); // 读取系统文件中的access token
		if( token == null ) {
			token = new WxAccessToken(); // 初始化一个无效凭证
		}
		accessToken.set(token);
		// 构建客户端创建器
		ClientBuilder clientBuilder = ClientBuilder.newBuilder();// 配置网络通信内容
		if( clientBuilder instanceof ResteasyClientBuilder ) {
			ResteasyClientBuilder rcb = (ResteasyClientBuilder) clientBuilder;
			if( executor != null ) {
				rcb.asyncExecutor(executor); // 配置线程池，默认使用线程池为固定大小最大10个线程
			}
			if( providerFactory != null ) {
				rcb.providerFactory(providerFactory);
			}
		}
		client = clientBuilder.build();
	}

	public String getBaseUrl() {
		return baseUrl;
	}
	
	/**
	 * 获取系统中常用的数据配置
	 * 返回系统中常量数据
	 */
	@SuppressWarnings("unchecked")
	public <T> T getAdapter(String key) {
		switch (key) {
		case WxConsts.APP_ID:// 设置微信公众号的appid
			return (T) getAppId();
		case WxConsts.APP_SECRET:// 设置微信公众号的app corpSecret
			return (T) getAppSecret();
		case WxConsts.ACCESS_TOKEN:// 设置微信公众号的 access token
			return (T) getAccessToken();
		case WxConsts.TOKEN:// 设置微信公众号的token
			return (T) getToken();
		case WxConsts.ENCODING_AES_KEY:// 设置消息加解密密钥
			return (T) getEncodingAesKey();
		default:
			return null;
		}
	}
	
	/**
	 * 获取系统的对象
	 */
	@SuppressWarnings("unchecked")
	public <T> T getAdapter( Class<T> type ) {
		if( type == WebTarget.class ) {
			return (T) client.target(getBaseUrl() );
		} else if( type == Client.class ) { 
			return (T)client;
		}
		return null;
	}
	
	/**
	 * 主要是为了防止不支持javaee7.0标准的反向内容注入
	 */
	public <T> void setAdapter(Class<T> type, T value) {
		if( type == ResteasyProviderFactory.class ) {
			providerFactory = (ResteasyProviderFactory) value;
		} else if( type == AccessTokenRest.class ) {
			accessTokenRest = (AccessTokenRest) value;
		}
	}

	public String getAppId() {
		return appId;
	}

	public String getAppSecret() {
		return appSecret;
	}

	public String getToken() {
		return token;
	}

	public String getEncodingAesKey() {
		return encodingAesKey;
	}
	
	/**
	 * 获取access token
	 */
	public String getAccessToken() {
		Status status = accessToken.get().checkValid();
		switch (status) {
		case NONE:
		case EXPIRED:// 同步刷新
			newAccessToken();
			break;
		case WILL_EXPIRE:// 异步刷新
			if (!synchronize.get()) {
				CompletableFuture.runAsync(this::newAccessToken, executor);
			}
		case VALID: break; // access token 正常有效
		}
		return accessToken.get().getAccessToken();
	}
	
	/**
	 * 清空access token
	 */
	public void clearAccessToken() {
		accessToken.set(new WxAccessToken()); // 清空access token
	}
	
	/**
	 * 更新access token
	 */
	private synchronized void newAccessToken() {
		if( accessToken.get().checkValid() == Status.VALID ) { return; } // 已经被其他线程同步过
		try {
			synchronize.set(true);  // 同步标识打开
			WxAccessToken token = accessTokenRest.getToken(WxConsts.GRANT_TYPE, appId, appSecret);
			accessToken.set(token);
		} finally {
			synchronize.set(false); // 同步表示关闭
		}
		if( WxConsts.DEBUG ) { //把 access token以对象的形式写入文件中
			writeAccessToken(accessToken.get());
		}
	}
	
	/**
	 * 临时缓存文件的名字
	 * @return
	 */
	protected abstract String getTempFileName();
	
	/**
	 * 把access token写入文件中，避免测试中频繁调用
	 * @param token
	 */
	protected void writeAccessToken(WxAccessToken token) {
		String filename = getTempFileName();
		if( filename == null || filename.isEmpty() ) { return; }
		try {
			FileOutputStream fos = new FileOutputStream(filename);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(token);
			oos.close();
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 从文件中读入access token
	 * @return
	 */
	protected WxAccessToken readAccessToken() {
		String filename = getTempFileName();
		if( filename == null || filename.isEmpty() ) { return null; }
		File file = new File(filename);
		if( !file.exists() ) { return null; }
		try {
			FileInputStream fis = new FileInputStream(file);
			ObjectInputStream ois = new ObjectInputStream(fis);
			WxAccessToken token = (WxAccessToken) ois.readObject();
			ois.close();
			fis.close();
			return token;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
}
