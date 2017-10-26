
#define WIN32_LEAN_AND_MEAN             // Exclude rarely-used stuff from Windows headers
#include <windows.h>

class LimitSingleInstance
{
public:
	LimitSingleInstance(TCHAR *strMutexName) { mutex = CreateMutex(NULL, FALSE, strMutexName);  error = GetLastError(); }
	~LimitSingleInstance() { if (mutex) { CloseHandle(mutex); mutex = NULL; } }
	BOOL IsAnotherInstanceRunning() { return (ERROR_ALREADY_EXISTS == error); }
private:
	DWORD  error;
	HANDLE mutex;
};

#define ENV_MAX 1024

LimitSingleInstance singleInstance(TEXT("Global\\{75d7b12a-3b5c-4f29-b706-d5e77ad61f1f}"));
CHAR   environment[ENV_MAX];
HWND   window;

/* this is an ungodly amount of cruft to simply capture the output of a process */
DWORD GetEnvironment()
{
	PROCESS_INFORMATION processInfo;
	STARTUPINFO info;
	HANDLE stdout_read, stdout_write, stdin_read, stdin_write;
	SECURITY_ATTRIBUTES saAttr;
	CHAR buf[ENV_MAX];
	DWORD cnt;

	ZeroMemory(&info, sizeof(STARTUPINFO));
	ZeroMemory(&environment, ENV_MAX);
	ZeroMemory(&buf, ENV_MAX);

	saAttr.nLength = sizeof(SECURITY_ATTRIBUTES);
	saAttr.bInheritHandle = TRUE;
	saAttr.lpSecurityDescriptor = NULL;

	if (!CreatePipe(&stdout_read, &stdout_write, &saAttr, 0)) return -1;
	if (!SetHandleInformation(stdout_read, HANDLE_FLAG_INHERIT, 0)) return -1;
	if (!CreatePipe(&stdin_read, &stdin_write, &saAttr, 0)) return -1;
	if (!SetHandleInformation(stdin_write, HANDLE_FLAG_INHERIT, 0)) return -1;

	info.cb = sizeof(STARTUPINFO);
	info.hStdError = stdout_write;
	info.hStdOutput = stdout_write;
	info.hStdInput = stdin_read;
	info.dwFlags |= STARTF_USESTDHANDLES;

	TCHAR cmd[] = TEXT("docker-machine env --shell cmd");
	if (!CreateProcess(NULL, cmd, NULL, NULL, TRUE, 0, NULL, NULL, &info, &processInfo))
		return -1;

	WaitForSingleObject(processInfo.hProcess, INFINITE);
	CloseHandle(processInfo.hProcess);
	CloseHandle(processInfo.hThread);

	if (!ReadFile(stdout_read, buf, ENV_MAX, &cnt, NULL)) return -1;

	char *next_token;
	char *token = strtok_s(buf, "\n", &next_token);
	char *envptr = environment;
	size_t left = ENV_MAX;
	while (true) {		
		if (token == NULL) break;
		if (strncmp(token, "SET ", 4)) break;
		token += 4;

		size_t len = strlen(token) + 1;
		strncpy_s(envptr, min(len, left), token, _TRUNCATE);
		envptr += len;
		left -= len;
		token = strtok_s(NULL, "\n", &next_token);
	}

	return 0;
}

DWORD RunAndWait(LPWSTR cmd, DWORD wait)
{
	STARTUPINFO info = { sizeof(info) };
	PROCESS_INFORMATION processInfo;
	if (!CreateProcess(NULL, cmd, NULL, NULL, TRUE, 0, NULL, NULL, &info, &processInfo))
		return -1;

	WaitForSingleObject(processInfo.hProcess, wait);
	CloseHandle(processInfo.hProcess);
	CloseHandle(processInfo.hThread);
	return 0;
}

DWORD WINAPI ShutdownThread(LPVOID lpParam)
{
	TCHAR quickdbstop[] = TEXT("docker kill -s INT db");
	TCHAR waitfordb[]   = TEXT("docker wait db");
	TCHAR machinestop[] = TEXT("docker-machine stop");

	if (GetEnvironment()               != 0) return -1;
	if (RunAndWait(quickdbstop,  2000) != 0) return -1;
	if (RunAndWait(waitfordb,    2000) != 0) return -1;
	if (RunAndWait(machinestop, 15000) != 0) return -1;
	PostMessage(window, WM_CLOSE, 0, 0);
	return 0;
}

LRESULT CALLBACK WndProc(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam)
{
	switch (message)
	{
		case WM_QUERYENDSESSION:
			ShutdownBlockReasonCreate(hWnd, L"Shutting down database and docker machine");
			CreateThread(NULL, 0, ShutdownThread, NULL, 0, NULL);
			return FALSE;
		case WM_DESTROY:
			ShutdownBlockReasonDestroy(hWnd);
			PostQuitMessage(0);
			break;
		default:
			return DefWindowProc(hWnd, message, wParam, lParam);
	}
	return 0;
}

int APIENTRY wWinMain(_In_ HINSTANCE hInstance, _In_opt_ HINSTANCE hPrevInstance, _In_ LPWSTR lpCmdLine, _In_ int nCmdShow)
{
	WNDCLASSEXW wcex;
	MSG msg;
	
	if (singleInstance.IsAnotherInstanceRunning())
		return FALSE;
	
	wcex.cbSize = sizeof(WNDCLASSEX);
	wcex.style = CS_HREDRAW | CS_VREDRAW;
	wcex.lpfnWndProc = WndProc;
	wcex.cbClsExtra = 0;
	wcex.cbWndExtra = 0;
	wcex.hInstance = hInstance;
	wcex.hIcon = NULL;
	wcex.hCursor = NULL;
	wcex.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
	wcex.lpszMenuName = NULL;
	wcex.lpszClassName = L"ignorewindow";
	wcex.hIconSm = NULL;
	RegisterClassExW(&wcex);

	window = CreateWindowEx(WS_EX_TOPMOST, L"ignorewindow", L"helper program to make sure vm shutsdown cleanly", 0, 0, 0, 0, 0, nullptr, nullptr, hInstance, nullptr);
	if (!window) return FALSE;

	SetProcessShutdownParameters(0x3FF, 0);
    while (GetMessage(&msg, nullptr, 0, 0))
    {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    return (int)msg.wParam;
}

