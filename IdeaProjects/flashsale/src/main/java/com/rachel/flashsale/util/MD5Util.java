package com.rachel.flashsale.util;

import org.apache.commons.codec.digest.DigestUtils;
//为什么要进行两次MD5
/*
* 第一次MD5：防止用户的明文密码在网络上传输，把用户的密码123456与固定的salt拼接后做MD5
* 第二次MD5：数据库被盗了，反差表，得到密码，把form表单提交的密码（）与随机salt拼接后做MD5
* */
public class MD5Util {
	
	public static String md5(String src) {
		return DigestUtils.md5Hex(src);//做一次MD5
	}
	
	private static final String salt = "1a2b3c4d";

	//把用户输入的密码与salt拼接，然后做MD5
	//这里的salt是固定的，服务器要识别
	public static String inputPassToFormPass(String inputPass) {
		String str = ""+salt.charAt(0)+salt.charAt(2) + inputPass +salt.charAt(5) + salt.charAt(4);
		return md5(str);//1a1234563b
	}

	//存入数据库的是一个随机的salt
	//把form表单密码再次做MD5加密
	public static String formPassToDBPass(String formPass, String salt) {
		String str = ""+salt.charAt(0)+salt.charAt(2) + formPass +salt.charAt(5) + salt.charAt(4);
		return md5(str);
	}


	public static String inputPassToDbPass(String inputPass, String saltDB) {
		String formPass = inputPassToFormPass(inputPass);
		String dbPass = formPassToDBPass(formPass, saltDB);
		return dbPass;
	}
	
	public static void main(String[] args) {
		//System.out.println(inputPassToFormPass("123456"));//d3b1294a61a07da9b49b6e22b2cbd7f9
		//System.out.println(formPassToDBPass(inputPassToFormPass("123456"),"1a2b3c4d"));
		//System.out.println(inputPassToDbPass("123456","1a2b3c4d"));
	}
	
}
