package com.xiuye.controller;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Date;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.xiuye.logger.Logger;
import com.xiuye.orm.Book;
import com.xiuye.orm.ReadingHistory;
import com.xiuye.orm.User;
import com.xiuye.orm.UserFavoriteBook;
import com.xiuye.service.BookService;
import com.xiuye.service.OnlineUserService;
import com.xiuye.service.ReadingHistoryService;
import com.xiuye.service.UserFavoriteBookService;
import com.xiuye.util.PdfOutputCustomPagesUtil;
import com.xiuye.util.PdfToImageUtil;

@Controller
public class ViewController {

	private static Logger log = Logger.getLogger(ViewController.class);

	@Resource
	private OnlineUserService onlineUserService;
	@Resource
	private BookService bookService;

	@Resource
	private ReadingHistoryService readingHistoryService;

	@Resource
	private UserFavoriteBookService ufbService;

	@RequestMapping("/currentTheme.do")
	@ResponseBody
	public String theme(HttpSession session) {
		String theme = (String) session.getAttribute("theme");
		if (theme == null) {
			theme = "swanky-purse";
			session.setAttribute("theme", theme);
		}
		return theme;
	}

	@RequestMapping("loginOut.do")
	@ResponseBody
	public void loginOut(HttpSession session) {

		User user = (User) session.getAttribute("user");

		// 删除数据库中的在线用户
		int effectRows = this.onlineUserService.cancelOnlineUserByUserid(user);

		log.info(effectRows >= 1 ? "在线用户退出" + user : "没有用户退出");
		user = null;
		session.setAttribute("user", user);
	}

	@RequestMapping("/readBook.do")
	@ResponseBody
	public void readBook(String bookid, HttpSession session,
			HttpServletResponse response, HttpServletRequest request) {

		User user = (User) session.getAttribute("user");
		Book book = this.bookService.getBookByBookid(bookid);
		if (book == null) {
			return;
		}
		String bookpath = book.getPath();
		// int beginIndex = bookpath.lastIndexOf(File.separator);
		// int endIndex = bookpath.lastIndexOf(".");
		//
		// String filename = bookpath.substring(beginIndex + 1, endIndex);
		// // System.out.println(filename);
		// String suffix = bookpath.substring(endIndex);
		// if (suffix != null && suffix.length() >= 2) {
		// suffix = suffix.toLowerCase();
		// if (suffix.contains("pdf")) {
		// filename += ".pdf";
		// } else if (suffix.contains("txt")) {
		// filename += ".txt";
		// } else if (suffix.contains("html"))
		// filename += ".html";
		// }
		//
		// try {
		// filename = URLEncoder.encode(filename, "UTF-8");
		// } catch (UnsupportedEncodingException e1) {
		// e1.printStackTrace();
		// }
		// log.info("文件名:" + filename);
		// response.setHeader("Content-Disposition", "attachment;filename="
		// + filename);

		if (user != null) {
			BufferedInputStream bis = null;
			FileInputStream fis = null;
			BufferedOutputStream bos = null;

			try {
				fis = new FileInputStream(bookpath);
				bis = new BufferedInputStream(fis);

				int length = bis.available();
				log.info("阅读文件大小:" + length + "字节(B)");

				response.setContentLength(length);

				bos = new BufferedOutputStream(response.getOutputStream());
				int i = 0;

				byte[] data = new byte[1024];

				while (bis.read(data) != -1) {

					bos.write(data);

					bos.flush();

				}

			} catch (FileNotFoundException e) {
				log.info("文件没找到:readBook原因:" + e.getMessage());
				// e.printStackTrace();
			} catch (IOException e) {
				log.info("IO异常操作:readBook原因:" + e.getMessage());
				// e.printStackTrace();
			} finally {
				try {
					fis.close();
					bis.close();
					// 不要关闭服务器输出，否则出错

				} catch (IOException e) {
				}
			}
		} else {
			// 非用户读取
			PdfOutputCustomPagesUtil
					.partOfPdfOutputPages(bookpath, 1, response);
		}

		int effectRows = 0;

		if (user != null) {
			UserFavoriteBook ufBook = new UserFavoriteBook();
			ufBook.setBookid(bookid);
			ufBook.setUserid(user.getUserid());
			effectRows = this.ufbService.addUserBookReadtimes(ufBook);
			log.info("用户增加阅读次数:" + effectRows + "次");

			int historycount = this.readingHistoryService
					.getReadingHistoryCount(user.getUserid(), bookid);

			ReadingHistory rh = new ReadingHistory();
			rh.setUserid(user.getUserid());
			rh.setBookid(bookid);
			rh.setReadingdate(new Date());
			rh.setReadtimes(historycount + 1);

			effectRows = this.readingHistoryService.addReadingHistoryBook(rh);
			log.info("历史阅读加" + effectRows + "次");
		}

		effectRows = this.bookService.addReadtime(book);
		log.info("总阅读次数加" + effectRows + "次");
	}

	@RequestMapping("/downloadBook.do")
	@ResponseBody
	public void downloadBook(String bookid, HttpServletResponse response,
			HttpServletRequest request) {

		Book book = this.bookService.getBookByBookid(bookid);
		if (book == null) {
			return;
		}
		String bookpath = book.getPath();
		int beginIndex = bookpath.lastIndexOf(File.separator);
		int endIndex = bookpath.lastIndexOf(".");

		String filename = bookpath.substring(beginIndex + 1, endIndex);
		// System.out.println(filename);
		String suffix = bookpath.substring(endIndex);
		if (suffix != null && suffix.length() >= 2) {
			suffix = suffix.toLowerCase();
			if (suffix.contains("pdf")) {
				filename += ".pdf";
			} else if (suffix.contains("txt")) {
				filename += ".txt";
			} else if (suffix.contains("html"))
				filename += ".html";
		}

		try {
			filename = URLEncoder.encode(filename, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}
		log.info("文件名:" + filename);
		response.setHeader("Content-Disposition", "attachment;filename="
				+ filename);

		BufferedInputStream bis = null;
		FileInputStream fis = null;
		BufferedOutputStream bos = null;

		try {
			fis = new FileInputStream(bookpath);
			bis = new BufferedInputStream(fis);

			int length = bis.available();
			response.setContentLength(length);

			bos = new BufferedOutputStream(response.getOutputStream());
			byte[] data = new byte[1024];
			while (bis.read(data) != -1) {
				bos.write(data);
				bos.flush();
			}

		} catch (FileNotFoundException e) {
			log.info("文件没找到:downloadBook原因:" + e.getMessage());
			// e.printStackTrace();
		} catch (IOException e) {
			log.info("IO异常操作:downloadBook原因:" + e.getMessage());
			// e.printStackTrace();
		} finally {
			try {
				fis.close();
				bis.close();
				// 不要关闭服务器输出，否则出错

			} catch (IOException e) {
			}
		}

	}

	@RequestMapping("/bookCover2.do")
	@ResponseBody
	public void bookCover2(String bookid, HttpServletResponse response) {

		bookid = URLDecoder.decode(bookid);

		Book book = this.bookService.getBookByBookid(bookid);

		String path = book.getPath();

		PdfToImageUtil.outPutCover(path, response);

	}

	@RequestMapping("/bookCover.do")
	@ResponseBody
	public void bookCover(String cover, HttpServletResponse response) {

		
		
		//System.out.println("ViewController:解码前:"+cover);
		
		try {
			cover = URLDecoder.decode(cover,"GBK");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}
		
	//	System.out.println("ViewController:解码后:"+cover);
		
		BufferedInputStream bis = null;
		try {
			bis = new BufferedInputStream(new FileInputStream(cover));
			int length = bis.available();
			response.setContentLength(length);
			BufferedOutputStream bos = new BufferedOutputStream(
					response.getOutputStream());

			byte[] data = new byte[1024];

			while (bis.read(data) > -1) {

				bos.write(data);
				bos.flush();

			}

			return;
		} catch (FileNotFoundException e) {
			log.info("文件没找到:bookCover原因:" + e.getMessage());
			//e.printStackTrace();
		} catch (IOException e) {
			log.info("IO异常操作:bookCover原因:" + e.getMessage());
			//e.printStackTrace();
		}

		finally {
			if (bis != null)
				try {
					bis.close();
				} catch (IOException e) {
				}

		}

		/**
		 * 上面错误的时候就加在下面的作为默认封面
		 * 
		 */
		try {
			bis = new BufferedInputStream(new FileInputStream(
					"F:/ComputerScience_and_TechnologyDocument/Cover/book.png"));
			int length = bis.available();
			response.setContentLength(length);
			BufferedOutputStream bos = new BufferedOutputStream(
					response.getOutputStream());

			byte[] data = new byte[1024];

			while (bis.read(data) > -1) {

				bos.write(data);
				bos.flush();

			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (bis != null)
				try {
					bis.close();
				} catch (IOException e) {
				}

		}
	}

}
