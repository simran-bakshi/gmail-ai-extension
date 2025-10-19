console.log("Email Writer Extension - Content script loaded.");

function createAIButton() {
    const button = document.createElement('div');
    button.className = 'T-I J-J5-Ji aoO v7 T-I-atl L3';
    button.style.marginRight = '8px';
    button.innerHTML = 'AI Reply';
    button.setAttribute('role', 'button');
    button.setAttribute('data-tooltip', 'Generate AI Reply');
    return button;
}

function getEmailContent() {
    const selectors = [
        '.h7',
        '.a3s.aiL',
        '.gmail_quote',
        '[role="presentation"]'
    ];
    for (const selector of selectors) {
        const content = document.querySelector(selector);
        if (content) {
            return content.innerText.trim();
        }
    }
    return '';
}

function findComposeToolbar() {
    const selectors = [
        '.btC',
        '.aDh',
        '[role="toolbar"]',
        '.gU.Up'
    ];
    for (const selector of selectors) {
        const toolbar = document.querySelector(selector);
        if (toolbar) {
            return toolbar;
        }
    }
    return null;
}

function injectButton() {
    const existingButton = document.querySelector('.ai-reply-button');
    if (existingButton) existingButton.remove();

    const toolbar = findComposeToolbar();
    if (!toolbar) {
        console.log("Toolbar not found");
        return;
    }

    console.log("Toolbar found, creating button");
    const button = createAIButton();
    button.classList.add('ai-reply-button');

    button.addEventListener('click', async () => {
        let response = null;
        try {
            button.innerHTML = 'Generating...';
            button.disabled = true;
            
            const emailContent = getEmailContent();
            console.log("Sending request to backend...");
            console.log("Email content:", emailContent);
            
            response = await fetch('http://localhost:8080/api/email/generate', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    emailContent: emailContent,
                    tone: "professional"
                })
            });
            
            console.log("Response status:", response.status);
            
            if (!response.ok) {
                const errorText = await response.text();
                console.error("Error response:", errorText);
                throw new Error(`Server error: ${response.status} - ${errorText}`);
            }
            
            const generatedReply = await response.text();
            console.log("Generated reply:", generatedReply);
            
            // Check if the reply contains an error message
            if (generatedReply.startsWith("Error:") || generatedReply.includes("Error generating")) {
                throw new Error(generatedReply);
            }
            
            const composeBox = document.querySelector('[role="textbox"][g_editable="true"]');
            if (composeBox) {
                composeBox.focus();
                document.execCommand('insertText', false, generatedReply);
                console.log("Reply inserted successfully");
            } else {
                console.error("Compose box not found");
                alert("Could not find compose box to insert reply");
            }
        } catch (error) {
            console.error("Full error:", error);
            let errorMessage = "Error generating reply";
            if (error.message && error.message.includes("Failed to fetch")) {
                errorMessage = "Cannot connect to server. Make sure Spring Boot is running on localhost:8080";
            } else if (response && response.status === 503) {
                errorMessage = "Server is unavailable (503). Check if your Spring Boot application is running.";
            } else if (error.message) {
                errorMessage = error.message;
            }
            alert(errorMessage);
        } finally {
            button.innerHTML = 'AI Reply';
            button.disabled = false;
        }
    });

    toolbar.insertBefore(button, toolbar.firstChild);
}

const observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
        const addedNodes = Array.from(mutation.addedNodes);
        const hasComposedElements = addedNodes.some(node =>
            node.nodeType === Node.ELEMENT_NODE &&
            (node.matches('.aDh, .btC, [role="dialog"]') ||
             node.querySelector('.aDh, .btC, [role="dialog"]'))
        );
        if (hasComposedElements) {
            console.log("Compose Window Detected");
            setTimeout(injectButton, 500);
        }
    }
});

observer.observe(document.body, { childList: true, subtree: true });