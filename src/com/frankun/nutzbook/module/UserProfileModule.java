package com.frankun.nutzbook.module;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.SQLException;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.nutz.dao.DaoException;
import org.nutz.dao.FieldFilter;
import org.nutz.dao.util.Daos;
import org.nutz.img.Images;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.log.Log;
import org.nutz.log.Logs;
import org.nutz.mvc.Mvcs;
import org.nutz.mvc.Scope;
import org.nutz.mvc.adaptor.JsonAdaptor;
import org.nutz.mvc.annotation.AdaptBy;
import org.nutz.mvc.annotation.At;
import org.nutz.mvc.annotation.Attr;
import org.nutz.mvc.annotation.By;
import org.nutz.mvc.annotation.Filters;
import org.nutz.mvc.annotation.GET;
import org.nutz.mvc.annotation.Ok;
import org.nutz.mvc.annotation.POST;
import org.nutz.mvc.annotation.Param;
import org.nutz.mvc.filter.CheckSession;
import org.nutz.mvc.impl.AdaptorErrorContext;
import org.nutz.mvc.upload.TempFile;
import org.nutz.mvc.upload.UploadAdaptor;

import com.frankun.nutzbook.bean.UserProfile;

@IocBean
@At("/user/profile")
@Filters(@By(type=CheckSession.class, args={"me", "/"}))
public class UserProfileModule extends BaseModule{
	
	private static final Log log = Logs.get();

	@At
	public UserProfile get(@Attr(scope=Scope.SESSION, value="me")int userId){
		UserProfile profile = Daos.ext(dao, FieldFilter.locked(UserProfile.class, "avatar")).fetch(UserProfile.class, userId);
		if (profile == null) {
			profile = new UserProfile();
			profile.setUserId(userId);
			profile.setCreateTime(new Date());
			profile.setUpdateTime(new Date());
			dao.insert(profile);
		}
		return profile;
	}
	
	@At
	@AdaptBy(type=JsonAdaptor.class)
	@Ok("void")
	public void update(@Param("..")UserProfile profile, @Attr(scope=Scope.SESSION, value="me")int userId){
		if (profile == null) {
			return;
		}
		profile.setUserId(userId);
		profile.setUpdateTime(new Date());
		profile.setAvatar(null); //头像不能通过此方法更新
		UserProfile old = get(userId);
		if (old.getEmail() == null) {	
			profile.setEmailChecked(false);
		}else{
			if (profile.getEmail() == null) {
				profile.setEmail(old.getEmail());
				profile.setEmailChecked(old.isEmailChecked());
			}else if (!profile.getEmail().equals(old.getEmail())) {
				profile.setEmailChecked(false);
			}else { 
				profile.setEmailChecked(old.isEmailChecked());
			}
		}
		Daos.ext(dao, FieldFilter.create(UserProfile.class, null, "avatar", true)).update(profile);
	}
	
	@AdaptBy(type=UploadAdaptor.class, args={"${app.root}/WEB-INF/tmp/user_avatar", "8192", "utf-8", "20000", "102400"})
	@POST
	@Ok(">>:/user/profile")
	@At("/avatar")
	public void uploadAvatar(@Param("file")TempFile tf,
			@Attr(scope=Scope.SESSION, value="me")int userId,
			AdaptorErrorContext errorContext){
		String msg = null;
		if (errorContext != null && errorContext.getAdaptorErr() != null) {
			msg = "文件大小不符合规定";
		}else if (tf == null) {
			msg = "空文件";
		}else {
			UserProfile profile = get(userId);
			try {
				BufferedImage image = Images.read(tf.getFile());
				image = Images.zoomScale(image, 128, 128, Color.WHITE);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
                Images.writeJpeg(image, out, 0.8f);
                profile.setAvatar(out.toByteArray());
                dao.update(profile, "^avatar$");
            } catch(DaoException e) {
                log.info("System Error", e);
                msg = "系统错误";
            } catch (Throwable e) {
                msg = "图片格式错误";
            }
        }
        if (msg != null){
        	Mvcs.getHttpSession().setAttribute("upload-error-msg", msg);
        }
	}
	
	@Ok("raw:jpg")
	@At("/avatar")
	@GET
	public Object readAvatar(@Attr(scope=Scope.SESSION, value="me")int userId, HttpServletRequest req) throws SQLException {
		UserProfile profile = Daos.ext(dao, FieldFilter.create(UserProfile.class, "^avatar$")).fetch(UserProfile.class, userId);
		if (profile == null || profile.getAvatar() == null) {
			return new File(req.getServletContext().getRealPath("/rs/user_avatar/none.jpg"));
 		}
		return profile.getAvatar();
	}
	
	@At("/")
	@GET
	@Ok("jsp:jsp.user.profile")
	public UserProfile index(@Attr(scope=Scope.SESSION, value="me")int userId){
		return get(userId);
	}
	
}
