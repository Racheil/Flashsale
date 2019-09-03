package com.rachel.flashsale.service;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import com.rachel.flashsale.redis.MiaoshaUserKey;
import com.rachel.flashsale.redis.RedisService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.rachel.flashsale.dao.MiaoshaUserDao;
import com.rachel.flashsale.domain.MiaoshaUser;
import com.rachel.flashsale.exception.GlobalException;
import com.rachel.flashsale.result.CodeMsg;
import com.rachel.flashsale.util.MD5Util;
import com.rachel.flashsale.util.UUIDUtil;
import com.rachel.flashsale.vo.LoginVo;

@Service
public class MiaoshaUserService {


	public static final String COOKI_NAME_TOKEN = "token";

	@Autowired
	MiaoshaUserDao miaoshaUserDao;

	@Autowired
	RedisService redisService;

	public MiaoshaUser getById(long id) {//对象级缓存
		//取缓存
		MiaoshaUser user = redisService.get(MiaoshaUserKey.getById, ""+id, MiaoshaUser.class);
		if(user != null) {
			return user;
		}
		//取数据库
		user = miaoshaUserDao.getById(id);
		if(user != null) {
			redisService.set(MiaoshaUserKey.getById, ""+id, user);
		}
		return user;
	}

	//用户对象修改密码
	public boolean updatePassword(String token, long id, String formPass) {
		//取user
		MiaoshaUser user = getById(id);
		if(user == null) {
			throw new GlobalException(CodeMsg.MOBILE_NOT_EXIST);
		}
		//更新数据库
		MiaoshaUser toBeUpdate = new MiaoshaUser();//新建一个对象去更新？
		toBeUpdate.setId(id);
		toBeUpdate.setPassword(MD5Util.formPassToDBPass(formPass, user.getSalt()));
		miaoshaUserDao.update(toBeUpdate);//1.先更新数据库
		//处理缓存
		redisService.delete(MiaoshaUserKey.getById, ""+id);//2.删除旧的缓存对象
		user.setPassword(toBeUpdate.getPassword());//只更新密码
		redisService.set(MiaoshaUserKey.token, token, user);//3.新添加一个缓存对象
		return true;
	}

     //根据token获取到具体的用户
	public MiaoshaUser getByToken(HttpServletResponse response, String token) {
		if(StringUtils.isEmpty(token)) {
			return null;
		}
		MiaoshaUser user = redisService.get(MiaoshaUserKey.token, token, MiaoshaUser.class);
		//延长有效期
		if(user != null) {
			addCookie(response, token, user); //用redis单独管理session,并没有存到容器中
		}
		return user;
	}


	public String login(HttpServletResponse response, LoginVo loginVo) {
		if(loginVo == null) {
			throw new GlobalException(CodeMsg.SERVER_ERROR);
		}
		String mobile = loginVo.getMobile();
		String formPass = loginVo.getPassword();//1a1234563b
		//判断手机号是否存在
		MiaoshaUser user = getById(Long.parseLong(mobile));//根据手机号在redis中查询获取user
		if(user == null) {
			throw new GlobalException(CodeMsg.MOBILE_NOT_EXIST);
		}
		//验证密码
		String dbPass = user.getPassword();//获取用户密码（d3b1294a61a07da9b49b6e22b2cbd7f9）
		String saltDB = user.getSalt();//获取salt 1a2c3b4d
		String calcPass = MD5Util.formPassToDBPass(formPass, saltDB);//把密码1a1234563b与随机salt拼接做一次MD5
		if(!calcPass.equals(dbPass)) {//dbPass数据库中保存的是两次MD5之后的值d3b1294a61a07da9b49b6e22b2cbd7f9
			throw new GlobalException(CodeMsg.PASSWORD_ERROR);
		}

		/*
		数据库中保存的是两次MD5之后的值d3b1294a61a07da9b49b6e22b2cbd7f9
		1）用户在登陆框输入手机号和密码123456
		2）然后前端页面上把密码做一次MD5之后发送给服务端
		3）服务器端接收到这个密码（1a1234563b）后，进行二次MD5
		4）服务端根据手机号查询到用户，以及用户在数据库中保存的密码
		5）服务器把接收的密码加密后与查询到的密码对比
		 */

		//生成cookie
		String token = UUIDUtil.uuid();
		addCookie(response, token, user);
		return token;

		//多台服务器，保证session同步
		//在用户登录成功后，给用户生成一个类似id的东西，token,标识用户
		//把token写到cookie当中，传递给客户端
		//客户端通过cookie上传token
		//服务端拿到token之后，根据token取到用户session信息


	}

	private void addCookie(HttpServletResponse response, String token, MiaoshaUser user) {
		redisService.set(MiaoshaUserKey.token, token, user);//token也是保存在缓存中的
		Cookie cookie = new Cookie(COOKI_NAME_TOKEN, token);
		cookie.setMaxAge(MiaoshaUserKey.token.expireSeconds());//设置cookie有效期
		cookie.setPath("/");//登录成功后，跳转到首页
		response.addCookie(cookie);//把cookie写到客户端
	}

}
