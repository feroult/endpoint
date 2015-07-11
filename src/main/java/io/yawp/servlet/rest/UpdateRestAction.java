package io.yawp.servlet.rest;

import io.yawp.commons.utils.EntityUtils;
import io.yawp.servlet.HttpException;

public class UpdateRestAction extends RestAction {

	public UpdateRestAction() {
		super("update");
	}

	@Override
	public void shield() {
		shield.protectUpdate();
	}

	@Override
	public Object action() {
		assert !isJsonArray();

		Object object = getObjectWithRightId();

		if (!satisfyShieldCondition(object)) {
			throw new HttpException(403);
		}

		save(object);

		return transform(object);
	}

	private Object getObjectWithRightId() {
		Object object = getObject();
		forceObjectIdFromRequest(object);
		return object;
	}

	private void forceObjectIdFromRequest(Object object) {
		EntityUtils.setId(object, id);
	}

}
