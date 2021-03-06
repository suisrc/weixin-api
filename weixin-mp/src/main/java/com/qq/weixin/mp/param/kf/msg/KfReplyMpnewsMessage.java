package com.qq.weixin.mp.param.kf.msg;

import com.qq.weixin.mp.param.kf.media.MediaId;

/**
 * 图文消息
 * 发送图文消息（点击跳转到图文消息页面） 
 * 
 * JSON解析
 */
public class KfReplyMpnewsMessage extends KfReplyBaseMessage {
	
    /**
     * 发送图文消息（点击跳转到图文消息页面） 
     */
    private MediaId mpnews;

    public KfReplyMpnewsMessage() {
        // setMsgtype(WxMsgType.mpnews.name());
        setMsgtype("mpnews");
    }

    /**
     * 获取发送图文消息（点击跳转到图文消息页面）
     * @return the mpnews
     */
    public MediaId getMpnews() {
        return mpnews;
    }

    /**
     * 设定发送图文消息（点击跳转到图文消息页面）
     * @param mpnews the mpnews to set
     */
    public void setMpnews(MediaId mpnews) {
        this.mpnews = mpnews;
    }

}
