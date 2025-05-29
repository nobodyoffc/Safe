package com.fc.fc_ajdk.data.fchData;

import com.fc.fc_ajdk.data.fcData.FcObject;

public class BlockMark extends FcObject {

	private String preBlockId;
	private Long height;
	private Long time;

	private Long size;		//block size
	private String status;

	//parsing info
	private Integer _fileOrder;		//The order number of the file that the block is located in.
	private Long _pointer;		//The position of the beginning of the block in the file.
	private Long orphanHeight;		//The number of orphan when writing this block to es. Only the point with _pend being 0 can be rollback to.


	public Long getTime() {
		return time;
	}

	public void setTime(Long time) {
		this.time = time;
	}
	//orphanHeight<=rollHeight  && height>rollHeight 的blockMark恢复为orphan

	public String getPreBlockId() {
		return preBlockId;
	}
	public void setPreBlockId(String preBlockId) {
		this.preBlockId = preBlockId;
	}
	public Long getHeight() {
		return height;
	}
	public void setHeight(Long height) {
		this.height = height;
	}
	public Integer get_fileOrder() {
		return _fileOrder;
	}
	public void set_fileOrder(Integer _fileOrder) {
		this._fileOrder = _fileOrder;
	}
	public Long get_pointer() {
		return _pointer;
	}
	public void set_pointer(Long _pointer) {
		this._pointer = _pointer;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public Long getSize() {
		return size;
	}
	public void setSize(Long size) {
		this.size = size;
	}
	public Long getOrphanHeight() {
		return orphanHeight;
	}
	public void setOrphanHeight(Long orphanHeight) {
		this.orphanHeight = orphanHeight;
	}

}
