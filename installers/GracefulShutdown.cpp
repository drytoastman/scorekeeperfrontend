
#define WIN32_LEAN_AND_MEAN             // Exclude rarely-used stuff from Windows headers
#include <windows.h>

BOOL shutdownNow;

LRESULT CALLBACK WndProc(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam)
{
	switch (message)
	{
		case WM_QUERYENDSESSION:
			ShutdownBlockReasonCreate(hWnd, L"Shutting down docker machine");
			shutdownNow = TRUE;
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
	MSG msg;
	shutdownNow = FALSE;
	
	WNDCLASSEXW wcex;
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

	HWND hWnd = CreateWindowEx(WS_EX_TOPMOST, L"ignorewindow", L"helper program to make sure vm shutsdown cleanly", 0, 0, 0, 0, 0, nullptr, nullptr, hInstance, nullptr);
	if (!hWnd) return FALSE;

	SetProcessShutdownParameters(0x3FF, 0);
    while (GetMessage(&msg, nullptr, 0, 0))
    {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
		if (shutdownNow) {
			MessageBox(NULL, L"Shutting down now.", L"Notice", MB_OK);
			shutdownNow = FALSE;
			//PostQuitMessage(0);
		}
    }

    return (int)msg.wParam;
}

