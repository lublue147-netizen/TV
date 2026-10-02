//go:build cgo

package main

/*
#include <jni.h>
#include <stdlib.h>
*/
import "C"

import (
	"github.com/lublue147-netizen/subscription/goproxy/server"
)

//export StartProxy
func StartProxy(port C.int) C.int {
	p := server.DefaultServer.Start(int(port))
	return C.int(p)
}

//export StopProxy
func StopProxy() {
	server.DefaultServer.Stop()
}

//export GetProxyPort
func GetProxyPort() C.int {
	return C.int(server.DefaultServer.GetPort())
}

// JNI bindings for Android

//export Java_com_github_catvod_proxy_GoProxy_nativeStart
func Java_com_github_catvod_proxy_GoProxy_nativeStart(env *C.JNIEnv, clazz C.jclass, port C.jint) C.jint {
	p := server.DefaultServer.Start(int(port))
	return C.jint(p)
}

//export Java_com_github_catvod_proxy_GoProxy_nativeStop
func Java_com_github_catvod_proxy_GoProxy_nativeStop(env *C.JNIEnv, clazz C.jclass) {
	server.DefaultServer.Stop()
}

//export Java_com_github_catvod_proxy_GoProxy_nativeGetPort
func Java_com_github_catvod_proxy_GoProxy_nativeGetPort(env *C.JNIEnv, clazz C.jclass) C.jint {
	return C.jint(server.DefaultServer.GetPort())
}
