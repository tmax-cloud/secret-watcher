package models;

import java.sql.Timestamp;

public class StateCheckInfo {
	int tryCount;
	Timestamp baseTime;
	static final int CHECK_TRY_COUNT = 20;
	static final long THRESHHOLD_TIME = 100; // milliseconds

	public StateCheckInfo() {
		super();
		this.tryCount = 0;
		this.baseTime = new Timestamp(System.currentTimeMillis());
	}
	public int getTryCount() {
		return tryCount;
	}
	public void setTryCount(int tryCount) {
		this.tryCount = tryCount;
	}
	public Timestamp getBaseTime() {
		return baseTime;
	}
	public void setBaseTime(Timestamp baseTime) {
		this.baseTime = baseTime;
	}
	
	public void checkThreadState() throws Exception {
		if(++tryCount == CHECK_TRY_COUNT) {
			Timestamp curTime = new Timestamp(System.currentTimeMillis());
			System.out.println("check times: " + (curTime.getTime() - baseTime.getTime()));

			if( curTime.getTime() - baseTime.getTime() < THRESHHOLD_TIME ) {
				System.out.println("Catch abnormal thread conditions!!");
				throw new Exception("abnormal");
			}

			baseTime = new Timestamp(System.currentTimeMillis());
			tryCount = 0;
		}
	}
}

