/*
 * Copyright (c) 2026 lax1dude / Eagler 26.2. All Rights Reserved.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 */

package net.lax1dude.eaglercraft.v1_8.internal.webgl2;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLElement;

import net.lax1dude.eaglercraft.v1_8.minecraft.EaglerHosted;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class WebXRController implements EaglerHosted.WebARBridge {

	public static final WebXRController INSTANCE = new WebXRController();

	@JSFunctor
	public interface FrameCallback extends JSObject {
		void onFrame(double timestamp);
	}

	private WebXRController() {
	}

	@Override
	public int viewCount() {
		return getViewCount();
	}

	@Override
	public boolean isActive() {
		return isSessionActive();
	}

	@Override
	public void selectView(final int viewIndex) {
		setCurrentView(viewIndex);
	}

	public static int currentViewIndex() {
		return getCurrentView();
	}

	@Override
	public boolean isPassthroughMode() {
		return getSetting(0) == 1.0F;
	}

	@Override
	public float sunAngleOffset() {
		return getSetting(6);
	}

	@Override
	public float sunIntensity() {
		return getSetting(7);
	}

	@Override
	public void applyView(final int viewIndex, final CameraRenderState cameraState, final WindowRenderState windowState) {
		int width = getViewportWidth(viewIndex);
		int height = getViewportHeight(viewIndex);
		if (width > 0 && height > 0) {
			windowState.width = width;
			windowState.height = height;
		}

		float px = getViewPosition(viewIndex, 0) - getAnchorCoordinate(0);
		float py = getViewPosition(viewIndex, 1) - getAnchorCoordinate(1);
		float pz = getViewPosition(viewIndex, 2) - getAnchorCoordinate(2);
		float angle = (float) Math.toRadians(-getAnchorYaw() - getSetting(4));
		float cos = (float) Math.cos(angle);
		float sin = (float) Math.sin(angle);
		float scale = Math.max(0.01F, Math.min(1.0F, getSetting(5)));
		double worldX = (cos * px - sin * pz) / scale - getSetting(1);
		net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
		double worldBaseY = minecraft.player != null ? minecraft.player.getY() : 0.0;
		double worldY = worldBaseY + py / scale - getSetting(2);
		double worldZ = (sin * px + cos * pz) / scale - getSetting(3);
		cameraState.pos = new Vec3(worldX, worldY, worldZ);
		cameraState.blockPos = BlockPos.containing(worldX, worldY, worldZ);
		cameraState.smartCull = false;

		float qx = getViewOrientation(viewIndex, 0);
		float qy = getViewOrientation(viewIndex, 1);
		float qz = getViewOrientation(viewIndex, 2);
		float qw = getViewOrientation(viewIndex, 3);
		float forwardX = -2.0F * (qx * qz + qw * qy);
		float forwardY = 2.0F * (qw * qx - qy * qz);
		float forwardZ = -1.0F + 2.0F * (qx * qx + qy * qy);
		float yaw = (float) Math.toDegrees(Math.atan2(-forwardX, forwardZ)) - getAnchorYaw() - getSetting(4);
		float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, forwardY))));
		cameraState.yRot = yaw;
		cameraState.xRot = pitch;
		cameraState.orientation.rotationYXZ((float) Math.PI - (float) Math.toRadians(yaw), (float) -Math.toRadians(pitch), 0.0F);
		cameraState.viewRotationMatrix.rotation(cameraState.orientation).invert();

		float[] projectionValues = new float[16];
		for (int i = 0; i < projectionValues.length; ++i) {
			projectionValues[i] = getViewProjection(viewIndex, i);
		}
		Matrix4f xrProjection = new Matrix4f().set(projectionValues);
		cameraState.projectionMatrix.set(new Matrix4f().scaling(1.0F, 1.0F, -1.0F).mul(xrProjection));
		cameraState.cullFrustum = new Frustum(cameraState.viewRotationMatrix, cameraState.projectionMatrix);
		cameraState.cullFrustum.prepare(worldX, worldY, worldZ);
	}

	public static void install(final HTMLElement parent, final HTMLCanvasElement canvas) {
		install0(parent, canvas);
	}

	@JSBody(params = { "parent", "canvas" }, script = ""
			+ "if (globalThis.__eaglerAR) return;"
			+ "var state = globalThis.__eaglerAR = { canvas: canvas, overlay: null, session: null, layer: null, referenceSpace: null, frame: null, views: [], pending: null, hitSource: null, hitPosition: null, anchorPosition: null, anchorYaw: 0, currentView: 0, settings: { mode: 0, groundDetection: false, offsetX: 0, offsetY: 0, offsetZ: 0, rotation: 0, scale: 1, sunAngle: 0, sunIntensity: 1 } };"
			+ "try { Object.assign(state.settings, JSON.parse(localStorage.getItem('eaglerAR.settings.v1') || '{}')); } catch (e) {}"
			+ "var overlay = document.createElement('div'); overlay.style.cssText = 'position:absolute;inset:0;z-index:20;pointer-events:none'; state.overlay = overlay;"
			+ "var shell = document.createElement('div'); shell.style.cssText = 'position:absolute;top:12px;right:12px;display:flex;gap:8px;align-items:flex-start;font:14px/1.35 system-ui,sans-serif;color:#14252a;pointer-events:none';"
			+ "var button = document.createElement('button'); button.type = 'button'; button.textContent = 'AR'; button.title = 'Start augmented reality'; button.setAttribute('aria-label', 'Start augmented reality');"
			+ "button.style.cssText = 'min-width:48px;height:42px;padding:0 12px;border:1px solid #d8e2e5;border-radius:6px;background:#f8fbfc;color:#13272c;font:600 15px/1 sans-serif;box-shadow:0 2px 8px #0005;touch-action:manipulation;pointer-events:auto';"
			+ "var settingsButton = document.createElement('button'); settingsButton.type = 'button'; settingsButton.textContent = 'Settings'; settingsButton.title = 'AR settings'; settingsButton.setAttribute('aria-label', 'Toggle AR settings'); settingsButton.setAttribute('aria-expanded', 'false');"
			+ "settingsButton.style.cssText = 'height:42px;padding:0 12px;border:1px solid #d8e2e5;border-radius:6px;background:#f8fbfc;color:#13272c;font:600 14px/1 sans-serif;box-shadow:0 2px 8px #0005;touch-action:manipulation;pointer-events:auto';"
			+ "var panel = document.createElement('section'); panel.hidden = true; panel.setAttribute('aria-label', 'Augmented reality settings'); panel.style.cssText = 'position:absolute;top:50px;right:0;width:min(280px,calc(100vw - 24px));max-height:min(60vh,360px);overflow:auto;padding:12px;background:#f8fbfc;border:1px solid #b8c9cc;border-radius:6px;box-shadow:0 8px 24px #0006;gap:9px;box-sizing:border-box;pointer-events:auto';"
			+ "panel.innerHTML = '<label>View mode<select id=ar-mode style=\"display:block;width:100%;margin-top:4px\"><option value=0>Tracked 6DOF world</option><option value=1>Passthrough, blocks and entities</option></select></label>'"
			+ "+ '<label><input id=ar-ground type=checkbox> Detect ground plane</label>'"
			+ "+ '<button id=ar-place type=button disabled>Place on detected plane</button>'"
			+ "+ '<fieldset><legend>Anchor offset</legend><div data-axis=x>X <button type=button data-step=-1 aria-label=\"Move anchor left on X\">−</button><output></output><button type=button data-step=1 aria-label=\"Move anchor right on X\">+</button></div>'"
			+ "+ '<div data-axis=y>Y <button type=button data-step=-1 aria-label=\"Move anchor down on Y\">−</button><output></output><button type=button data-step=1 aria-label=\"Move anchor up on Y\">+</button></div>'"
			+ "+ '<div data-axis=z>Z <button type=button data-step=-1 aria-label=\"Move anchor back on Z\">−</button><output></output><button type=button data-step=1 aria-label=\"Move anchor forward on Z\">+</button></div></fieldset>'"
			+ "+ '<label>Yaw <output id=ar-rotation></output><input id=ar-rotation-input type=range min=0 max=360 step=1></label>'"
			+ "+ '<label>Scale <output id=ar-scale></output><input id=ar-scale-input type=range min=0.01 max=1 step=0.01></label>'"
			+ "+ '<label>Sun angle <output id=ar-sun-angle></output><input id=ar-sun-angle-input type=range min=0 max=360 step=1></label>'"
			+ "+ '<label>Sun intensity <output id=ar-sun-intensity></output><input id=ar-sun-intensity-input type=range min=0 max=3 step=0.05></label>'"
			+ "+ '<output id=ar-status aria-live=polite></output>';"
			+ "shell.appendChild(button); shell.appendChild(settingsButton); shell.appendChild(panel); overlay.appendChild(shell); parent.appendChild(overlay);"
			+ "var mode = panel.querySelector('#ar-mode'), ground = panel.querySelector('#ar-ground'), place = panel.querySelector('#ar-place'), status = panel.querySelector('#ar-status'); state.placeButton = place; state.status = status;"
			+ "var ranges = [['rotation','ar-rotation-input','ar-rotation','°'],['scale','ar-scale-input','ar-scale',''],['sunAngle','ar-sun-angle-input','ar-sun-angle','°'],['sunIntensity','ar-sun-intensity-input','ar-sun-intensity','']];"
			+ "function save() { try { localStorage.setItem('eaglerAR.settings.v1', JSON.stringify(state.settings)); } catch (e) {} }"
			+ "function refresh() { mode.value = '' + state.settings.mode; ground.checked = !!state.settings.groundDetection; place.disabled = !state.settings.groundDetection || !state.hitPosition; panel.querySelector('[data-axis=x] output').textContent = state.settings.offsetX.toFixed(2); panel.querySelector('[data-axis=y] output').textContent = state.settings.offsetY.toFixed(2); panel.querySelector('[data-axis=z] output').textContent = state.settings.offsetZ.toFixed(2); for (var i = 0; i < ranges.length; ++i) { var r = ranges[i]; panel.querySelector('#' + r[1]).value = state.settings[r[0]]; panel.querySelector('#' + r[2]).textContent = Number(state.settings[r[0]]).toFixed(r[0] === 'scale' || r[0] === 'sunIntensity' ? 2 : 0) + r[3]; } }"
			+ "function setPanelOpen(open) { panel.hidden = !open; panel.style.display = open ? 'grid' : 'none'; settingsButton.setAttribute('aria-expanded', '' + open); }"
			+ "refresh(); settingsButton.addEventListener('click', function() { setPanelOpen(panel.hidden); });"
			+ "mode.addEventListener('change', function() { state.settings.mode = Number(mode.value); save(); }); ground.addEventListener('change', function() { state.settings.groundDetection = ground.checked; save(); refresh(); });"
			+ "panel.addEventListener('click', function(event) { var target = event.target; if (target === place) { if (state.settings.groundDetection && state.hitPosition) { state.anchorPosition = { x: state.hitPosition.x, y: state.hitPosition.y, z: state.hitPosition.z }; state.anchorYaw = state.hitYaw || 0; status.textContent = 'Anchor placed on detected ground'; } return; } var axisControl = target && target.closest ? target.closest('[data-axis]') : null; if (axisControl && target.dataset.step) { var axis = axisControl.dataset.axis; state.settings['offset' + axis.toUpperCase()] = Math.max(-100, Math.min(100, state.settings['offset' + axis.toUpperCase()] + Number(target.dataset.step) * 0.1)); save(); refresh(); } });"
			+ "function bindRange(r) { panel.querySelector('#' + r[1]).addEventListener('input', function(event) { state.settings[r[0]] = Number(event.target.value); save(); refresh(); }); }"
			+ "for (var i = 0; i < ranges.length; ++i) { bindRange(ranges[i]); }"
			+ "var pendingSession = null; button.addEventListener('click', function() {"
			+ "if (state.session) { state.session.end(); return; }"
			+ "if (!navigator.xr || !canvas.getContext('webgl2')) { status.textContent = 'Immersive AR is unavailable in this browser/device.'; setPanelOpen(true); return; }"
			+ "button.disabled = true; status.textContent = 'Requesting camera and motion permission...'; setPanelOpen(false);"
			+ "navigator.xr.requestSession('immersive-ar', { requiredFeatures: ['dom-overlay'], optionalFeatures: ['local-floor', 'hit-test', 'plane-detection', 'anchors'], domOverlay: { root: overlay } }).then(function(session) { pendingSession = session;"
			+ "var gl = canvas.getContext('webgl2'); return gl.makeXRCompatible().then(function() { var layer = new XRWebGLLayer(session, gl, { alpha: true, depth: true, antialias: false });"
			+ "return session.requestReferenceSpace('local-floor').catch(function() { return session.requestReferenceSpace('local'); }).then(function(referenceSpace) { state.session = session; pendingSession = null; state.layer = layer; state.referenceSpace = referenceSpace; session.updateRenderState({ baseLayer: layer });"
			+ "session.requestReferenceSpace('viewer').then(function(viewerSpace) { return session.requestHitTestSource({ space: viewerSpace }); }).then(function(source) { state.hitSource = source; }).catch(function() { state.hitSource = null; if (state.status) state.status.textContent = 'Ground hit testing is unavailable on this device.'; });"
			+ "button.textContent = 'Exit AR'; button.title = 'Exit augmented reality'; button.setAttribute('aria-label', 'Exit augmented reality'); button.disabled = false; status.textContent = 'AR session active';"
			+ "session.addEventListener('end', function() { state.session = null; pendingSession = null; state.layer = null; state.referenceSpace = null; state.frame = null; state.views = []; state.hitSource = null; state.hitPosition = null; state.anchorPosition = null; button.textContent = 'AR'; button.title = 'Start augmented reality'; button.setAttribute('aria-label', 'Start augmented reality'); button.disabled = false; status.textContent = 'AR session ended'; refresh(); var pending = state.pending; state.pending = null; if (pending) pending(0); }, { once: true }); pendingSession = null; }); });"
			+ "}).catch(function(error) { if (pendingSession) { pendingSession.end(); pendingSession = null; } button.disabled = false; status.textContent = 'AR could not start: ' + (error && error.message ? error.message : error); }); });"
			+ "")
	private static native void install0(HTMLElement parent, HTMLCanvasElement canvas);

	@JSBody(params = { "callback" }, script = ""
			+ "var s = globalThis.__eaglerAR; if (!s || !s.session) return false; s.pending = callback;"
			+ "s.session.requestAnimationFrame(function(t, frame) { if (s.pending !== callback) return; s.pending = null; s.frame = frame;"
			+ "var pose = frame.getViewerPose(s.referenceSpace); s.hitPosition = null; s.hitPose = null;"
			+ "if (pose) {"
			+ "if (!s.anchorPosition) { var p = pose.transform.position; s.anchorPosition = { x: p.x, y: p.y - 1.6, z: p.z }; var q = pose.transform.orientation; s.anchorYaw = Math.atan2(2 * (q.w * q.y + q.x * q.z), 1 - 2 * (q.y * q.y + q.x * q.x)); }"
			+ "if (s.hitSource && s.settings.groundDetection) { var hits = frame.getHitTestResults(s.hitSource); for (var h = 0; h < hits.length; ++h) { var hitPose = hits[h].getPose(s.referenceSpace); if (!hitPose) continue; var hq = hitPose.transform.orientation; var normalY = 1 - 2 * (hq.x * hq.x + hq.z * hq.z); if (normalY > 0.7) { var hp = hitPose.transform.position; s.hitPosition = { x: hp.x, y: hp.y, z: hp.z }; s.hitPose = hitPose; s.hitYaw = Math.atan2(2 * (hq.w * hq.y + hq.x * hq.z), 1 - 2 * (hq.y * hq.y + hq.x * hq.x)); break; } } }"
			+ "if (s.placeButton) s.placeButton.disabled = !s.settings.groundDetection || !s.hitPosition; if (s.status && s.settings.groundDetection && s.hitPosition) s.status.textContent = 'Ground detected';"
			+ "var nextViews = []; for (var i = 0; i < pose.views.length; ++i) { var view = pose.views[i]; var viewport = s.layer.getViewport(view); nextViews.push({ x: viewport.x, y: viewport.y, width: viewport.width, height: viewport.height, projection: view.projectionMatrix, position: view.transform.position, orientation: view.transform.orientation, viewMatrix: view.transform.inverse.matrix }); }"
			+ "if (nextViews.length > 0) s.views = nextViews;"
			+ "} callback(t); }); return true;")
	private static native boolean requestXRFrame0(FrameCallback callback);

	public static boolean requestXRFrame(final FrameCallback callback) {
		return requestXRFrame0(callback);
	}

	@JSBody(params = {}, script = "var s = globalThis.__eaglerAR; return !!(s && s.session);")
	public static native boolean isSessionActive();

	@JSBody(params = {}, script = "var s = globalThis.__eaglerAR; return s && s.layer ? s.layer.framebuffer : null;")
	public static native JSObject getFramebuffer();

	@JSBody(params = {}, script = "var s = globalThis.__eaglerAR; return s && s.views ? s.views.length : 0;")
	public static native int getViewCount();

	@JSBody(params = { "index" }, script = "var v = globalThis.__eaglerAR.views[index]; return v ? v.x : 0;")
	public static native int getViewportX(int index);

	@JSBody(params = { "index" }, script = "var v = globalThis.__eaglerAR.views[index]; return v ? v.y : 0;")
	public static native int getViewportY(int index);

	@JSBody(params = { "index" }, script = "var v = globalThis.__eaglerAR.views[index]; return v ? v.width : 0;")
	public static native int getViewportWidth(int index);

	@JSBody(params = { "index" }, script = "var v = globalThis.__eaglerAR.views[index]; return v ? v.height : 0;")
	public static native int getViewportHeight(int index);

	@JSBody(params = { "view" }, script = "var v = globalThis.__eaglerAR.views[view]; return v ? v.viewMatrix : null;")
	public static native JSObject getViewMatrix(int view);

	@JSBody(params = { "index" }, script = "var s = globalThis.__eaglerAR; return s ? (index === 0 ? s.settings.mode : index === 1 ? s.settings.offsetX : index === 2 ? s.settings.offsetY : index === 3 ? s.settings.offsetZ : index === 4 ? s.settings.rotation : index === 5 ? s.settings.scale : index === 6 ? s.settings.sunAngle : s.settings.sunIntensity) : (index === 5 || index === 7 ? 1 : 0);")
	public static native float getSetting(int index);

	@JSBody(params = {}, script = "var s = globalThis.__eaglerAR; return !!(s && s.settings.groundDetection && s.hitPose);")
	public static native boolean isGroundDetectionEnabled();

	public static void drawGroundMarker(final WebGL2RenderingContextExt gl) {
		drawGroundMarker0(gl);
	}

	@JSBody(params = { "gl" }, script = ""
			+ "var s = globalThis.__eaglerAR, v = s && s.views[s.currentView]; if (!s || !v || !s.settings.groundDetection || !s.hitPose) return;"
			+ "var r = s.marker; if (!r || r.gl !== gl) {"
			+ "function shader(type, source) { var sh = gl.createShader(type); gl.shaderSource(sh, source); gl.compileShader(sh); return gl.getShaderParameter(sh, gl.COMPILE_STATUS) ? sh : null; }"
			+ "var vs = shader(gl.VERTEX_SHADER, '#version 300 es\\nlayout(location=0) in vec3 aPosition; uniform mat4 uMvp; void main(){ gl_Position=uMvp*vec4(aPosition,1.0); }');"
			+ "var fs = shader(gl.FRAGMENT_SHADER, '#version 300 es\\nprecision mediump float; out vec4 outColor; void main(){ outColor=vec4(0.05,1.0,0.2,0.38); }'); if (!vs || !fs) return;"
			+ "var program = gl.createProgram(); gl.attachShader(program,vs); gl.attachShader(program,fs); gl.linkProgram(program); if (!gl.getProgramParameter(program,gl.LINK_STATUS)) return;"
			+ "var vao=gl.createVertexArray(), buffer=gl.createBuffer(); gl.bindVertexArray(vao); gl.bindBuffer(gl.ARRAY_BUFFER,buffer); gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-0.35,0,-0.35,0.35,0,-0.35,-0.35,0,0.35,0.35,0,0.35]),gl.STATIC_DRAW); gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0,3,gl.FLOAT,false,0,0); gl.bindVertexArray(null);"
			+ "r = s.marker = { gl: gl, program: program, vao: vao, mvp: gl.getUniformLocation(program,'uMvp') }; }"
			+ "function multiply(a,b) { var o=new Float32Array(16); for(var c=0;c<4;c++) for(var row=0;row<4;row++) o[c*4+row]=a[row]*b[c*4]+a[4+row]*b[c*4+1]+a[8+row]*b[c*4+2]+a[12+row]*b[c*4+3]; return o; }"
			+ "var matrix=multiply(v.projection,multiply(v.viewMatrix,s.hitPose.transform.matrix)); gl.useProgram(r.program); gl.bindVertexArray(r.vao); gl.uniformMatrix4fv(r.mvp,false,matrix); gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA); gl.enable(gl.DEPTH_TEST); gl.depthFunc(gl.LEQUAL); gl.depthMask(false); gl.drawArrays(gl.TRIANGLE_STRIP,0,4); gl.depthMask(true); gl.disable(gl.DEPTH_TEST); gl.disable(gl.BLEND); gl.bindVertexArray(null); gl.useProgram(null);")
	private static native void drawGroundMarker0(WebGL2RenderingContextExt gl);

	@JSBody(params = { "axis" }, script = "var p = globalThis.__eaglerAR && globalThis.__eaglerAR.anchorPosition; return p ? (axis === 0 ? p.x : axis === 1 ? p.y : p.z) : 0;")
	public static native float getAnchorCoordinate(int axis);

	@JSBody(params = {}, script = "var s = globalThis.__eaglerAR; return s ? (s.anchorYaw || 0) * 180 / Math.PI : 0;")
	public static native float getAnchorYaw();

	@JSBody(params = { "view" }, script = "var s = globalThis.__eaglerAR; if (s) s.currentView = view;")
	private static native void setCurrentView(int view);

	@JSBody(params = {}, script = "var s = globalThis.__eaglerAR; return s ? s.currentView : 0;")
	private static native int getCurrentView();

	@JSBody(params = { "view", "index" }, script = "var v = globalThis.__eaglerAR.views[view]; return v && v.position ? (index === 0 ? v.position.x : index === 1 ? v.position.y : v.position.z) : 0;")
	public static native float getViewPosition(int view, int index);

	@JSBody(params = { "view", "index" }, script = "var v = globalThis.__eaglerAR.views[view]; return v && v.orientation ? (index === 0 ? v.orientation.x : index === 1 ? v.orientation.y : index === 2 ? v.orientation.z : v.orientation.w) : 0;")
	public static native float getViewOrientation(int view, int index);

	@JSBody(params = { "view", "index" }, script = "var v = globalThis.__eaglerAR.views[view]; return v && v.projection ? v.projection[index] : 0;")
	public static native float getViewProjection(int view, int index);
}