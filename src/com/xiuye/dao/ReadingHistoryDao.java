package com.xiuye.dao;

import java.util.List;

import com.xiuye.orm.ReadingHistory;

public interface ReadingHistoryDao {

	List<ReadingHistory> findAll();
	
}